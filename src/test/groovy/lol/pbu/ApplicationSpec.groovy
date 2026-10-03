package lol.pbu

import spock.lang.Specification

class ApplicationSpec extends Specification {

    def "configureLogbackStatusListener sets OnErrorConsoleStatusListener if property is not configured"() {
        given: "logback.statusListenerClass is unset"
        String original = System.getProperty("logback.statusListenerClass")
        System.clearProperty("logback.statusListenerClass")

        when: "configuration is executed"
        Application.configureLogbackStatusListener()

        then: "OnErrorConsoleStatusListener is set to protect MCP STDIO stream"
        System.getProperty("logback.statusListenerClass") == "ch.qos.logback.core.status.OnErrorConsoleStatusListener"

        cleanup:
        if (original != null) {
            System.setProperty("logback.statusListenerClass", original)
        } else {
            System.clearProperty("logback.statusListenerClass")
        }
    }

    def "configureLogbackStatusListener preserves user-configured status listener"() {
        given: "a user-configured status listener"
        String original = System.getProperty("logback.statusListenerClass")
        System.setProperty("logback.statusListenerClass", "custom.CustomStatusListener")

        when: "configuration is executed"
        Application.configureLogbackStatusListener()

        then: "user-defined property is preserved without override"
        System.getProperty("logback.statusListenerClass") == "custom.CustomStatusListener"

        cleanup:
        if (original != null) {
            System.setProperty("logback.statusListenerClass", original)
        } else {
            System.clearProperty("logback.statusListenerClass")
        }
    }

    def "Application instance can be created"() {
        expect:
        new Application() != null
    }
}
