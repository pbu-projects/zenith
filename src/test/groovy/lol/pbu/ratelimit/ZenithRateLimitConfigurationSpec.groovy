package lol.pbu.ratelimit

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ZenithRateLimitConfigurationSpec extends Specification {

    def "default configuration uses retry mode and 60 second window"() {
        given:
        def config = new ZenithRateLimitConfiguration()

        expect:
        config.mode == "retry"
        config.maxWindowSeconds == 60L
        !config.isFailFast()
    }

    def "isFailFast recognizes various fail-fast aliases case-insensitively"() {
        given:
        def config = new ZenithRateLimitConfiguration()

        when:
        config.setMode(mode)

        then:
        config.isFailFast() == expected

        where:
        mode          | expected
        "fail-fast"   | true
        "FAIL-FAST"   | true
        "fail_fast"   | true
        "FAIL_FAST"   | true
        "failfast"    | true
        "immediate"   | true
        "IMMEDIATE"   | true
        "retry"       | false
        "RETRY"       | false
        ""            | false
        null          | false
    }

    def "windowSeconds setter aliases maxWindowSeconds"() {
        given:
        def config = new ZenithRateLimitConfiguration()

        when:
        config.setWindowSeconds(45L)

        then:
        config.maxWindowSeconds == 45L
    }

    def "binds properties from application configuration"() {
        given:
        def context = ApplicationContext.run([
                "zenith.rate-limit.mode": "fail-fast",
                "zenith.rate-limit.max-window-seconds": "120"
        ])

        when:
        def config = context.getBean(ZenithRateLimitConfiguration)

        then:
        config.mode == "fail-fast"
        config.maxWindowSeconds == 120L
        config.isFailFast()

        cleanup:
        context?.close()
    }
}
