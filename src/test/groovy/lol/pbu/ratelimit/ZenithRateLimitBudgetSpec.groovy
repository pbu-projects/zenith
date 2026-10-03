package lol.pbu.ratelimit

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class ZenithRateLimitBudgetSpec extends Specification {

    def "creates budget with configuration defaults when no arguments supplied"() {
        given:
        def config = new ZenithRateLimitConfiguration()
        config.setMode("retry")
        config.setMaxWindowSeconds(60)

        when:
        def budget = ZenithRateLimitBudget.create(config, null)

        then:
        budget.mode == "retry"
        budget.maxWindow == Duration.ofSeconds(60)
        !budget.isFailFast()
        budget.canWait(Duration.ofSeconds(10))
        !budget.canWait(Duration.ofSeconds(61))
    }

    def "tool arguments override rate limit mode and window"() {
        given:
        def config = new ZenithRateLimitConfiguration()

        when:
        def budget = ZenithRateLimitBudget.create(config, [
                (modeKey): "fail-fast",
                (windowKey): windowVal
        ])

        then:
        budget.mode == "fail-fast"
        budget.isFailFast()
        budget.maxWindow == Duration.ofSeconds(expectedSeconds)

        where:
        modeKey            | windowKey            | windowVal | expectedSeconds
        "rateLimitMode"    | "rateLimitWindow"    | 30        | 30
        "rate_limit_mode"  | "rate_limit_window"  | "45"      | 45
        "rateLimitMode"    | "windowSeconds"      | 90L       | 90
        "rate_limit_mode"  | "window_seconds"     | "120"     | 120
    }

    def "canWait accounts for elapsed time from start"() {
        given:
        def startTime = Instant.now().minusSeconds(20) // 20s already elapsed
        def budget = new ZenithRateLimitBudget(startTime, Duration.ofSeconds(60), "retry")

        expect:
        budget.elapsedTime.seconds >= 20
        budget.canWait(Duration.ofSeconds(35)) // 20 + 35 = 55 <= 60
        !budget.canWait(Duration.ofSeconds(45)) // 20 + 45 = 65 > 60
    }

    def "thread-local current budget management"() {
        given:
        def budget = new ZenithRateLimitBudget(Instant.now(), Duration.ofSeconds(30), "fail-fast")

        when:
        ZenithRateLimitBudget.setCurrentBudget(budget)

        then:
        ZenithRateLimitBudget.getCurrentBudget() == budget

        when:
        ZenithRateLimitBudget.clearCurrentBudget()

        then:
        ZenithRateLimitBudget.getCurrentBudget() == null
    }
}
