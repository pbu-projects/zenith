package lol.pbu.ratelimit

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class ZenithRateLimitCombinationsSpec extends Specification {

    @Unroll
    def "PICT combination: envMode=#configMode, envWindow=#configWindow, argMode=#argMode, argWindow=#argWindow -> effectiveMode=#expectedMode, effectiveWindow=#expectedWindow"() {
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

        then: "tool argument is the determining means of setting the value"
        budget.mode == expectedMode
        budget.maxWindow == Duration.ofSeconds(expectedWindow)
        budget.isFailFast() == expectedFailFast

        and: "warning is emitted when overrides or conflicts occur, but execution never fails"
        if (expectWarning) {
            budget.warning.isPresent()
        } else {
            !budget.warning.isPresent()
        }

        where:
        configMode    | configWindow | argMode       | argWindow | expectedMode  | expectedWindow | expectedFailFast | expectWarning
        // Group 1: Env mode = retry, Env window = 60
        "retry"       | 60           | null          | null      | "retry"       | 60             | false            | false
        "retry"       | 60           | null          | 30L       | "retry"       | 30             | false            | true
        "retry"       | 60           | "retry"       | null      | "retry"       | 60             | false            | false
        "retry"       | 60           | "retry"       | 30L       | "retry"       | 30             | false            | true
        "retry"       | 60           | "immediate"   | null      | "immediate"   | 60             | true             | true
        "retry"       | 60           | "immediate"   | 30L       | "immediate"   | 30             | true             | true

        // Group 2: Env mode = retry, Env window = 120
        "retry"       | 120          | null          | null      | "retry"       | 120            | false            | false
        "retry"       | 120          | null          | 30L       | "retry"       | 30             | false            | true
        "retry"       | 120          | "retry"       | null      | "retry"       | 120            | false            | false
        "retry"       | 120          | "retry"       | 30L       | "retry"       | 30             | false            | true
        "retry"       | 120          | "immediate"   | null      | "immediate"   | 120            | true             | true
        "retry"       | 120          | "immediate"   | 30L       | "immediate"   | 30             | true             | true

        // Group 3: Env mode = immediate (fail-fast), Env window = 60
        "immediate"   | 60           | null          | null      | "immediate"   | 60             | true             | false
        "immediate"   | 60           | null          | 30L       | "retry"       | 30             | false            | true
        "immediate"   | 60           | "retry"       | null      | "retry"       | 60             | false            | true
        "immediate"   | 60           | "retry"       | 30L       | "retry"       | 30             | false            | true
        "immediate"   | 60           | "immediate"   | null      | "immediate"   | 60             | true             | false
        "immediate"   | 60           | "immediate"   | 30L       | "immediate"   | 30             | true             | true

        // Group 4: Env mode = immediate (fail-fast), Env window = 120
        "immediate"   | 120          | null          | null      | "immediate"   | 120            | true             | false
        "immediate"   | 120          | null          | 30L       | "retry"       | 30             | false            | true
        "immediate"   | 120          | "retry"       | null      | "retry"       | 120            | false            | true
        "immediate"   | 120          | "retry"       | 30L       | "retry"       | 30             | false            | true
        "immediate"   | 120          | "immediate"   | null      | "immediate"   | 120            | true             | false
        "immediate"   | 120          | "immediate"   | 30L       | "immediate"   | 30             | true             | true
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
