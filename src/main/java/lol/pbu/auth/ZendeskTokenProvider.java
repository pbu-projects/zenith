package lol.pbu.auth;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages OAuth 2.0 token acquisition and caching for Zendesk.
 * Supports static Bearer tokens or automatic Client Credentials grant.
 */
@Singleton
public class ZendeskTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTokenProvider.class);

    private final String zendeskUrl;
    private final String clientId;
    private final String clientSecret;
    private final String staticToken;
    private final String scope;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile long tokenExpiresAtMs = 0L;
    private final AtomicReference<Mono<String>> inFlightRefresh = new AtomicReference<>();

    public ZendeskTokenProvider(
            @Value("${micronaut.http.services.zendesk.url}") String zendeskUrl,
            @Value("${micronaut.http.services.zendesk.oauth.client-id:}") String clientId,
            @Value("${micronaut.http.services.zendesk.oauth.client-secret:}") String clientSecret,
            @Value("${micronaut.http.services.zendesk.oauth.token:}") String staticToken,
            @Value("${micronaut.http.services.zendesk.oauth.scope:read write}") String scope,
            ObjectMapper objectMapper) {
        this(zendeskUrl, clientId, clientSecret, staticToken, scope, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public ZendeskTokenProvider(
            String zendeskUrl,
            String clientId,
            String clientSecret,
            String staticToken,
            String scope,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.zendeskUrl = zendeskUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.staticToken = staticToken;
        this.scope = scope;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Returns a valid Bearer token reactively, refreshing via client_credentials grant if expired.
     */
    public Mono<String> getValidTokenMono() {
        if (StringUtils.isNotEmpty(staticToken)) {
            return Mono.just(staticToken);
        }

        if (StringUtils.isEmpty(clientId) || StringUtils.isEmpty(clientSecret)) {
            return Mono.empty();
        }

        long now = System.currentTimeMillis();
        String currentToken = this.cachedToken;
        if (currentToken != null && now < (this.tokenExpiresAtMs - 60_000L)) {
            return Mono.just(currentToken);
        }

        return getOrCreateRefreshMono();
    }

    /**
     * Synchronous bridge for backwards compatibility.
     */
    public Optional<String> getValidToken() {
        try {
            return getValidTokenMono().blockOptional();
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException || e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.error("Failed to acquire Zendesk OAuth token due to thread interruption", e);
            } else {
                log.error("Failed to acquire Zendesk OAuth token synchronously", e);
            }
            this.cachedToken = null;
            this.tokenExpiresAtMs = 0L;
            return Optional.empty();
        }
    }

    private synchronized Mono<String> getOrCreateRefreshMono() {
        long now = System.currentTimeMillis();
        String currentToken = this.cachedToken;
        if (currentToken != null && now < (this.tokenExpiresAtMs - 60_000L)) {
            return Mono.just(currentToken);
        }

        Mono<String> existing = inFlightRefresh.get();
        if (existing != null) {
            return existing;
        }

        Mono<String> newRefresh = executeTokenRefresh()
                .doFinally(signalType -> inFlightRefresh.set(null))
                .cache();

        inFlightRefresh.set(newRefresh);
        return newRefresh;
    }

    private Mono<String> executeTokenRefresh() {
        return Mono.defer(() -> {
            String baseUrl = zendeskUrl;
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String tokenUrl = baseUrl + "/oauth/tokens";
            log.info("Requesting new OAuth token from Zendesk: {}", tokenUrl);

            String formBody = "grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            CompletableFuture<HttpResponse<String>> future =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            return Mono.fromFuture(future)
                    .flatMap(this::handleTokenResponse);
        }).onErrorResume(e -> {
            log.error("Failed to acquire Zendesk OAuth token using client_credentials", e);
            this.cachedToken = null;
            this.tokenExpiresAtMs = 0L;
            return Mono.empty();
        });
    }

    private Mono<String> handleTokenResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                OAuthTokenResponse tokenResponse = objectMapper.readValue(response.body(), OAuthTokenResponse.class);
                this.cachedToken = tokenResponse.accessToken();
                long expiresInSeconds = (tokenResponse.expiresIn() != null) ? tokenResponse.expiresIn() : 1800L;
                this.tokenExpiresAtMs = System.currentTimeMillis() + (expiresInSeconds * 1000L);
                log.info("Successfully acquired Zendesk OAuth token, valid for {} seconds", expiresInSeconds);
                return Mono.justOrEmpty(this.cachedToken);
            } catch (IOException e) {
                return Mono.error(e);
            }
        } else {
            return Mono.error(new IllegalStateException("Zendesk OAuth token request failed with HTTP "
                    + response.statusCode() + ": " + response.body()));
        }
    }
}
