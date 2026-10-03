package lol.pbu.ratelimit

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class ZenithRateLimitCombinationsSpec extends Specification {

    @Unroll
    def "PICT combination: envMode=#configMode, envWindow=#configWindow, argMode=#argMode, argWindow=#argWindow"() {
        given: "environment configuration"
        def config = new ZenithRateLimitConfiguration()
        config.setMode(configMode)
        config.setMaxWindowSeconds(configWindow)

        and: "tool arguments map"
        Map<String, Object> arguments = [:]
        if (argMode != null) {
            arguments.put("rateLimitMode", argMode)
        }
        if (argWindow != null) {
            arguments.put("rateLimitWindow", argWindow)
        }

        when: "budget is resolved"
        def budget = ZenithRateLimitBudget.create(config, arguments.isEmpty() ? null : arguments)

        then: "tool argument is the determining means of setting mode and window"
        def configIsFailFast = ZenithRateLimitBudget.isFailFastMode(configMode)
        def argIsFailFast = argMode != null && ZenithRateLimitBudget.isFailFastMode(argMode)

        def expectedMode = (argMode != null) ? argMode : (argWindow != null && configIsFailFast ? ZenithRateLimitConfiguration.MODE_RETRY : configMode)
        def expectedWindow = (argWindow != null) ? argWindow : configWindow
        def expectedFailFast = ZenithRateLimitBudget.isFailFastMode(expectedMode)

        def expectedWarning = (argMode != null && argWindow != null && (argIsFailFast || configIsFailFast || argWindow != configWindow)) ||
                (argMode != null && argWindow == null && (argIsFailFast != configIsFailFast)) ||
                (argMode == null && argWindow != null && (configIsFailFast || argWindow != configWindow))

        budget.mode == expectedMode
        budget.maxWindow == Duration.ofSeconds(expectedWindow)
        budget.isFailFast() == expectedFailFast
        budget.warning.isPresent() == expectedWarning

        where:
        [configMode, configWindow, argMode, argWindow] << [
                ["retry", "immediate"],
                [60L, 120L],
                [null, "retry", "immediate"],
                [null, 30L]
        ].combinations()
    }

    def "when env var is immediate but tool arg provides window, warning is logged and retry is enabled"() {
        given:
        def config = new ZenithRateLimitConfiguration()
        config.setMode("immediate")
        config.setMaxWindowSeconds(60)

        when:
        def budget = ZenithRateLimitBudget.create(config, ["rateLimitWindow": 30])

        then:
        budget.mode == "retry"
        budget.maxWindow == Duration.ofSeconds(30)
        !budget.isFailFast()
        budget.warning.isPresent()
        budget.warning.get().contains("Overriding mode to 'retry' bounded by 30s")
    }

    def "when tool arg provides both immediate mode and window, warning is logged but immediate mode is preserved"() {
        given:
        def config = new ZenithRateLimitConfiguration()
        config.setMode("retry")

        when:
        def budget = ZenithRateLimitBudget.create(config, [
                "rateLimitMode": "immediate",
                "rateLimitWindow": 45
        ])

        then:
        budget.mode == "immediate"
        budget.isFailFast()
        budget.warning.isPresent()
        budget.warning.get().contains("In fail-fast mode, retries are disabled and the window is not used")
    }
}
