package lol.pbu.ratelimit;

import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks the execution time budget and rate limit behavior for a tool action.
 * Allows tools to bound their total execution time (default 60s) across retries.
 * Tool arguments serve as the determining means of setting rate limit mode and window,
 * overriding environment variable defaults with a warning when conflicts or overrides occur.
 */
public class ZenithRateLimitBudget {

    private static final Logger log = LoggerFactory.getLogger(ZenithRateLimitBudget.class);

    public static final String KEY = "zenith.rate-limit.budget";
    private static final ThreadLocal<ZenithRateLimitBudget> CURRENT = new ThreadLocal<>();

    private final Instant startTime;
    private final Duration maxWindow;
    private final String mode;
    @Nullable private final String warning;

    public ZenithRateLimitBudget(Instant startTime, Duration maxWindow, String mode) {
        this(startTime, maxWindow, mode, null);
    }

    public ZenithRateLimitBudget(Instant startTime, Duration maxWindow, String mode, @Nullable String warning) {
        this.startTime = startTime != null ? startTime : Instant.now();
        this.maxWindow = maxWindow != null ? maxWindow : Duration.ofSeconds(ZenithRateLimitConfiguration.DEFAULT_MAX_WINDOW_SECONDS);
        this.mode = mode != null ? mode : ZenithRateLimitConfiguration.MODE_RETRY;
        this.warning = warning;
    }

    public static ZenithRateLimitBudget create(
            @Nullable ZenithRateLimitConfiguration config,
            @Nullable Map<String, Object> arguments
    ) {
        String configMode = config != null ? config.getMode() : ZenithRateLimitConfiguration.MODE_RETRY;
        long configWindow = config != null ? config.getMaxWindowSeconds() : ZenithRateLimitConfiguration.DEFAULT_MAX_WINDOW_SECONDS;
        boolean configIsFailFast = config != null && config.isFailFast();

        String explicitArgMode = extractExplicitMode(arguments);
        Long explicitArgWindow = extractExplicitWindow(arguments);

        ResolvedBudget resolved = resolveBudgetSettings(configMode, configWindow, configIsFailFast, explicitArgMode, explicitArgWindow);
        return new ZenithRateLimitBudget(Instant.now(), Duration.ofSeconds(resolved.window()), resolved.mode(), resolved.warning());
    }

    private static ResolvedBudget resolveBudgetSettings(
            String configMode,
            long configWindow,
            boolean configIsFailFast,
            @Nullable String explicitArgMode,
            @Nullable Long explicitArgWindow
    ) {
        if (explicitArgMode != null && explicitArgWindow != null) {
            return resolveBothArgsProvided(configMode, configWindow, configIsFailFast, explicitArgMode, explicitArgWindow);
        }
        if (explicitArgMode != null) {
            return resolveModeArgOnly(configMode, configWindow, configIsFailFast, explicitArgMode);
        }
        if (explicitArgWindow != null) {
            return resolveWindowArgOnly(configMode, configWindow, configIsFailFast, explicitArgWindow);
        }
        return new ResolvedBudget(configMode, configWindow, null);
    }

    private static ResolvedBudget resolveBothArgsProvided(
            String configMode,
            long configWindow,
            boolean configIsFailFast,
            String explicitArgMode,
            long explicitArgWindow
    ) {
        boolean argIsFailFast = isFailFastMode(explicitArgMode);
        String warning = null;
        if (argIsFailFast) {
            warning = String.format("Tool call specified rateLimitMode='%s' alongside a retry window of %ds. In fail-fast mode, retries are disabled and the window is not used.", explicitArgMode, explicitArgWindow);
            log.warn("{}", warning);
        } else if (configIsFailFast) {
            warning = String.format("Tool call argument rateLimitMode='%s' (with window %ds) overrides configured environment mode '%s'.", explicitArgMode, explicitArgWindow, configMode);
            log.warn("{}", warning);
        } else if (explicitArgWindow != configWindow) {
            warning = String.format("Tool call argument rateLimitWindow=%ds overrides configured environment window of %ds.", explicitArgWindow, configWindow);
            log.warn("{}", warning);
        }
        return new ResolvedBudget(explicitArgMode, explicitArgWindow, warning);
    }

    private static ResolvedBudget resolveModeArgOnly(
            String configMode,
            long configWindow,
            boolean configIsFailFast,
            String explicitArgMode
    ) {
        boolean argIsFailFast = isFailFastMode(explicitArgMode);
        String warning = null;
        if (argIsFailFast != configIsFailFast) {
            warning = String.format("Tool call argument rateLimitMode='%s' overrides configured environment mode '%s'.", explicitArgMode, configMode);
            log.warn("{}", warning);
        }
        return new ResolvedBudget(explicitArgMode, configWindow, warning);
    }

    private static ResolvedBudget resolveWindowArgOnly(
            String configMode,
            long configWindow,
            boolean configIsFailFast,
            long explicitArgWindow
    ) {
        String effectiveMode;
        String warning = null;
        if (configIsFailFast) {
            effectiveMode = ZenithRateLimitConfiguration.MODE_RETRY;
            warning = String.format("Rate limit mode was configured as '%s' via environment, but tool argument specified a retry window of %ds. Overriding mode to 'retry' bounded by %ds.", configMode, explicitArgWindow, explicitArgWindow);
            log.warn("{}", warning);
        } else {
            effectiveMode = configMode;
            if (explicitArgWindow != configWindow) {
                warning = String.format("Tool call argument rateLimitWindow=%ds overrides configured environment window of %ds.", explicitArgWindow, configWindow);
                log.warn("{}", warning);
            }
        }
        return new ResolvedBudget(effectiveMode, explicitArgWindow, warning);
    }

    @Nullable
    private static String extractExplicitMode(@Nullable Map<String, Object> arguments) {
        if (arguments == null) {
            return null;
        }
        Object argMode = arguments.get("rateLimitMode");
        if (argMode == null) {
            argMode = arguments.get("rate_limit_mode");
        }
        if (argMode instanceof String str && !str.isBlank()) {
            return str.trim();
        }
        return null;
    }

    @Nullable
    private static Long extractExplicitWindow(@Nullable Map<String, Object> arguments) {
        if (arguments == null) {
            return null;
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
            return num.longValue();
        }
        if (argWindow instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException _) {
                // Ignore malformed override
            }
        }
        return null;
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

    public Optional<String> getWarning() {
        return Optional.ofNullable(warning);
    }

    public boolean isFailFast() {
        return isFailFastMode(mode);
    }

    public static boolean isFailFastMode(@Nullable String mode) {
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

    private record ResolvedBudget(String mode, long window, @Nullable String warning) {
    }
}
