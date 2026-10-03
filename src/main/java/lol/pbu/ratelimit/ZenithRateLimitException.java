package lol.pbu.ratelimit;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Exception thrown when a Zendesk API rate limit (HTTP 429) cannot be handled within
 * configured parameters (e.g. fail-fast mode, exceeded action time window, or exhausted retries).
 */
public class ZenithRateLimitException extends HttpClientResponseException {

    public enum Reason {
        FAIL_FAST,
        WINDOW_EXCEEDED,
        RETRIES_EXHAUSTED
    }

    private final Reason reason;
    private final Duration waitDuration;
    private final Duration maxWindow;
    private final Duration elapsedTime;
    private final Instant nextAllowableCall;

    public ZenithRateLimitException(
            String message,
            HttpResponse<?> response,
            Reason reason,
            Duration waitDuration,
            Duration maxWindow,
            Duration elapsedTime,
            Instant nextAllowableCall
    ) {
        super(message, response);
        this.reason = reason;
        this.waitDuration = waitDuration;
        this.maxWindow = maxWindow;
        this.elapsedTime = elapsedTime;
        this.nextAllowableCall = nextAllowableCall;
    }

    public Reason getReason() {
        return reason;
    }

    public Optional<Duration> getWaitDuration() {
        return Optional.ofNullable(waitDuration);
    }

    public Optional<Duration> getMaxWindow() {
        return Optional.ofNullable(maxWindow);
    }

    public Optional<Duration> getElapsedTime() {
        return Optional.ofNullable(elapsedTime);
    }

    public Optional<Instant> getNextAllowableCall() {
        return Optional.ofNullable(nextAllowableCall);
    }
}
