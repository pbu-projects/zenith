package lol.pbu.ratelimit;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import lol.pbu.ratelimit.ZenithRateLimitException.Reason;
import lol.pbu.z4j.client.RateLimitFilter;
import lol.pbu.z4j.ratelimit.RateLimitConfiguration;
import lol.pbu.z4j.ratelimit.RateLimitTracker;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.time.Instant;

/**
 * Custom rate limit filter replacing z4j's default {@link RateLimitFilter}.
 * Bounds total action execution time to a configurable window (default 60s)
 * and supports immediate fail-fast mode with rich next-allowable-call context.
 */
@ClientFilter("/**")
@Replaces(RateLimitFilter.class)
public class ZenithRateLimitFilter extends RateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(ZenithRateLimitFilter.class);

    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String HEADER_RATELIMIT_RESET = "ratelimit-reset";
    private static final String HEADER_X_RATE_LIMIT_RESET = "X-Rate-Limit-Reset";
    private static final String HEADER_ZENDESK_RATELIMIT_RESET = "zendesk-ratelimit-reset";
    private static final int MAX_ATTEMPTS = 5;

    private final RateLimitTracker tracker;
    private final RateLimitConfiguration z4jConfig;
    private final ZenithRateLimitConfiguration zenithConfig;

    public ZenithRateLimitFilter(
            RateLimitTracker tracker,
            RateLimitConfiguration z4jConfig,
            ZenithRateLimitConfiguration zenithConfig
    ) {
        super(tracker, z4jConfig);
        this.tracker = tracker;
        this.z4jConfig = z4jConfig;
        this.zenithConfig = zenithConfig;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        return Flux.deferContextual(ctx -> {
            ZenithRateLimitBudget budget = resolveBudget(ctx);
            return executeWithRetry(request, chain, 0, budget);
        });
    }

    @SuppressWarnings("unchecked")
    private Flux<HttpResponse<?>> executeWithRetry(
            MutableHttpRequest<?> request,
            ClientFilterChain chain,
            int attempt,
            ZenithRateLimitBudget budget
    ) {
        Flux<HttpResponse<?>> proceedFlux = Flux.defer(() -> {
            if (attempt == 0 && !budget.isFailFast() && z4jConfig.isAutoWaitEnabled() && tracker.isApproachingLimit(z4jConfig.getApproachThreshold())) {
                long waitSec = z4jConfig.getWaitDurationSeconds();
                Duration waitDuration = Duration.ofSeconds(waitSec);
                if (budget.canWait(waitDuration)) {
                    log.warn("Rate limit approaching threshold (<= {}). Auto-wait enabled. Pausing for {} seconds before sending request to {}",
                            z4jConfig.getApproachThreshold(), waitSec, request.getPath());
                    return Mono.delay(waitDuration)
                            .flatMapMany(v -> (Publisher<HttpResponse<?>>) chain.proceed(request));
                } else {
                    log.warn("Rate limit approaching threshold, but auto-wait of {}s exceeds remaining action budget. Proceeding immediately.", waitSec);
                }
            }
            return (Publisher<HttpResponse<?>>) chain.proceed(request);
        });

        return proceedFlux
                .doOnNext(response -> handleResponse(request, response))
                .onErrorResume(HttpClientResponseException.class, ex -> {
                    handleResponse(request, ex.getResponse());
                    if (ex.getResponse() != null && ex.getStatus() != null && ex.getStatus().getCode() == 429) {
                        return handle429(request, chain, attempt, budget, ex);
                    }
                    return Flux.error(ex);
                });
    }

    private Flux<HttpResponse<?>> handle429(
            MutableHttpRequest<?> request,
            ClientFilterChain chain,
            int attempt,
            ZenithRateLimitBudget budget,
            HttpClientResponseException ex
    ) {
        HttpResponse<?> response = ex.getResponse();
        HttpHeaders headers = response != null ? response.getHeaders() : null;
        long waitSec = resolveWaitDurationSeconds(headers);
        Duration waitDuration = Duration.ofSeconds(waitSec);
        Instant nextAllowableCall = Instant.now().plus(waitDuration);

        if (budget.isFailFast()) {
            log.warn("HTTP 429 received for {} {}. Rate limit mode is 'fail-fast'. Failing immediately without retry. Next allowable call at: {}",
                    request.getMethodName(), request.getPath(), nextAllowableCall);
            String msg = String.format(
                    "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Rate limit mode is configured to 'fail-fast'. You must wait %d seconds before sending further requests. Next allowable call at: %s.",
                    waitSec,
                    nextAllowableCall
            );
            return Flux.error(new ZenithRateLimitException(
                    msg,
                    response,
                    Reason.FAIL_FAST,
                    waitDuration,
                    budget.getMaxWindow(),
                    budget.getElapsedTime(),
                    nextAllowableCall
            ));
        }

        if (!budget.canWait(waitDuration)) {
            log.warn("HTTP 429 received for {} {}. Wait of {}s would exceed maximum action window of {}s (elapsed: {}s). Aborting retries.",
                    request.getMethodName(), request.getPath(), waitSec, budget.getMaxWindow().toSeconds(), budget.getElapsedTime().toSeconds());
            String msg = String.format(
                    "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Action timed out after rate limit wait exceeded maximum window of %d seconds. You must wait %d seconds before sending further requests. Next allowable call at: %s.",
                    budget.getMaxWindow().toSeconds(),
                    waitSec,
                    nextAllowableCall
            );
            return Flux.error(new ZenithRateLimitException(
                    msg,
                    response,
                    Reason.WINDOW_EXCEEDED,
                    waitDuration,
                    budget.getMaxWindow(),
                    budget.getElapsedTime(),
                    nextAllowableCall
            ));
        }

        if (attempt >= MAX_ATTEMPTS) {
            log.warn("HTTP 429 received for {} {}. Maximum retries ({}) exhausted. Aborting retries.",
                    request.getMethodName(), request.getPath(), MAX_ATTEMPTS);
            String msg = String.format(
                    "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted after %d attempts. You must wait %d seconds before sending further requests. Next allowable call at: %s.",
                    attempt,
                    waitSec,
                    nextAllowableCall
            );
            return Flux.error(new ZenithRateLimitException(
                    msg,
                    response,
                    Reason.RETRIES_EXHAUSTED,
                    waitDuration,
                    budget.getMaxWindow(),
                    budget.getElapsedTime(),
                    nextAllowableCall
            ));
        }

        log.warn("HTTP 429 received for {} {}. Retrying (attempt {}) after {} seconds...",
                request.getMethodName(), request.getPath(), attempt + 1, waitSec);
        return Mono.delay(waitDuration)
                .flatMapMany(v -> executeWithRetry(request, chain, attempt + 1, budget));
    }

    private ZenithRateLimitBudget resolveBudget(ContextView ctx) {
        if (ctx.hasKey(ZenithRateLimitBudget.KEY)) {
            return ctx.get(ZenithRateLimitBudget.KEY);
        }
        ZenithRateLimitBudget threadLocalBudget = ZenithRateLimitBudget.getCurrentBudget();
        if (threadLocalBudget != null) {
            return threadLocalBudget;
        }
        return new ZenithRateLimitBudget(
                Instant.now(),
                Duration.ofSeconds(zenithConfig.getMaxWindowSeconds()),
                zenithConfig.getMode()
        );
    }

    private long resolveWaitDurationSeconds(HttpHeaders headers) {
        if (headers != null) {
            Long waitSec = parseDurationSeconds(headers, HEADER_RETRY_AFTER, HEADER_RATELIMIT_RESET, HEADER_X_RATE_LIMIT_RESET, HEADER_ZENDESK_RATELIMIT_RESET);
            if (waitSec != null && waitSec > 0) {
                return waitSec;
            }
        }
        long configuredWait = z4jConfig.getWaitDurationSeconds();
        return configuredWait > 0 ? configuredWait : 60L;
    }

    private Long parseDurationSeconds(HttpHeaders headers, String... candidateNames) {
        for (String name : candidateNames) {
            String value = headers.get(name);
            if (value != null && !value.isBlank()) {
                try {
                    return Long.parseLong(value.trim());
                } catch (NumberFormatException _) {
                    try {
                        double d = Double.parseDouble(value.trim());
                        return (long) Math.ceil(d);
                    } catch (NumberFormatException __) {
                        // ignore and try next candidate
                    }
                }
            }
        }
        return null;
    }
}
