package lol.pbu.ratelimit;

import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Tracks the execution time budget and rate limit behavior for a tool action.
 * Allows tools to bound their total execution time (default 60s) across retries.
 */
public class ZenithRateLimitBudget {

    public static final String KEY = "zenith.rate-limit.budget";
    private static final ThreadLocal<ZenithRateLimitBudget> CURRENT = new ThreadLocal<>();

    private final Instant startTime;
    private final Duration maxWindow;
    private final String mode;

    public ZenithRateLimitBudget(Instant startTime, Duration maxWindow, String mode) {
        this.startTime = startTime != null ? startTime : Instant.now();
        this.maxWindow = maxWindow != null ? maxWindow : Duration.ofSeconds(ZenithRateLimitConfiguration.DEFAULT_MAX_WINDOW_SECONDS);
        this.mode = mode != null ? mode : ZenithRateLimitConfiguration.MODE_RETRY;
    }

    public static ZenithRateLimitBudget create(
            @Nullable ZenithRateLimitConfiguration config,
            @Nullable Map<String, Object> arguments
    ) {
        String mode = config != null ? config.getMode() : ZenithRateLimitConfiguration.MODE_RETRY;
        long windowSeconds = config != null ? config.getMaxWindowSeconds() : ZenithRateLimitConfiguration.DEFAULT_MAX_WINDOW_SECONDS;

        if (arguments != null) {
            Object argMode = arguments.get("rateLimitMode");
            if (argMode == null) {
                argMode = arguments.get("rate_limit_mode");
            }
            if (argMode instanceof String str && !str.isBlank()) {
                mode = str.trim();
            }

            Object argWindow = arguments.get("rateLimitWindow");
            if (argWindow == null) {
                argWindow = arguments.get("rate_limit_window");
            }
            if (argWindow == null) {
                argWindow = arguments.get("windowSeconds");
            }
            if (argWindow == null) {
                argWindow = arguments.get("window_seconds");
            }
            if (argWindow instanceof Number num) {
                windowSeconds = num.longValue();
            } else if (argWindow instanceof String str && !str.isBlank()) {
                try {
                    windowSeconds = Long.parseLong(str.trim());
                } catch (NumberFormatException _) {
                    // Ignore malformed override, fallback to configured window
                }
            }
        }

        return new ZenithRateLimitBudget(Instant.now(), Duration.ofSeconds(windowSeconds), mode);
    }

    public static ZenithRateLimitBudget getCurrentBudget() {
        return CURRENT.get();
    }

    public static void setCurrentBudget(ZenithRateLimitBudget budget) {
        if (budget != null) {
            CURRENT.set(budget);
        } else {
            CURRENT.remove();
        }
    }

    public static void clearCurrentBudget() {
        CURRENT.remove();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Duration getMaxWindow() {
        return maxWindow;
    }

    public String getMode() {
        return mode;
    }

    public boolean isFailFast() {
        if (mode == null) {
            return false;
        }
        String clean = mode.trim().toLowerCase();
        return ZenithRateLimitConfiguration.MODE_FAIL_FAST.equals(clean)
                || "fail_fast".equals(clean)
                || "failfast".equals(clean)
                || "immediate".equals(clean);
    }

    public Duration getElapsedTime() {
        return Duration.between(startTime, Instant.now());
    }

    public Duration getRemainingTime() {
        Duration elapsed = getElapsedTime();
        Duration remaining = maxWindow.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean canWait(Duration waitDuration) {
        if (waitDuration == null) {
            return true;
        }
        return getElapsedTime().plus(waitDuration).compareTo(maxWindow) <= 0;
    }
}
