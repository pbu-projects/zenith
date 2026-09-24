package lol.pbu.tools

import spock.lang.Specification

class IllegalArgumentExceptionMcpErrorMapperSpec extends Specification {

    IllegalArgumentExceptionMcpErrorMapper mapper = new IllegalArgumentExceptionMcpErrorMapper()

    def "canMap returns true only for IllegalArgumentException and subclasses"() {
        expect:
        mapper.canMap(IllegalArgumentException)
        mapper.canMap(NumberFormatException)
        !mapper.canMap(RuntimeException)
        !mapper.canMap(NullPointerException)
    }

    def "maps IllegalArgumentException with message to -32602"() {
        given:
        def ex = new IllegalArgumentException("Invalid ticket ID")

        when:
        def error = mapper.map(ex)

        then:
        error.jsonRpcError.code == -32602
        error.message == "Invalid ticket ID"
    }

    def "maps IllegalArgumentException with empty message using default"() {
        given:
        def ex = new IllegalArgumentException("")

        when:
        def error = mapper.map(ex)

        then:
        error.jsonRpcError.code == -32602
        error.message == "Invalid argument"
    }
}
