package lol.pbu.ratelimit;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration properties for Zenith rate limit handling on Zendesk API calls.
 */
@ConfigurationProperties(ZenithRateLimitConfiguration.PREFIX)
public class ZenithRateLimitConfiguration {

    public static final String PREFIX = "zenith.rate-limit";
    public static final String MODE_RETRY = "retry";
    public static final String MODE_FAIL_FAST = "fail-fast";
    public static final long DEFAULT_MAX_WINDOW_SECONDS = 60L;

    private String mode = MODE_RETRY;
    private long maxWindowSeconds = DEFAULT_MAX_WINDOW_SECONDS;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public long getMaxWindowSeconds() {
        return maxWindowSeconds;
    }

    public void setMaxWindowSeconds(long maxWindowSeconds) {
        this.maxWindowSeconds = maxWindowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.maxWindowSeconds = windowSeconds;
    }

    public boolean isFailFast() {
        if (mode == null) {
            return false;
        }
        String clean = mode.trim().toLowerCase();
        return MODE_FAIL_FAST.equals(clean)
                || "fail_fast".equals(clean)
                || "failfast".equals(clean)
                || "immediate".equals(clean);
    }
}
