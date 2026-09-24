package lol.pbu.tools

import spock.lang.Specification

class DefaultThrowableMcpErrorMapperSpec extends Specification {

    DefaultThrowableMcpErrorMapper mapper = new DefaultThrowableMcpErrorMapper()

    def "canMap returns true for any Throwable"() {
        expect:
        mapper.canMap(RuntimeException)
        mapper.canMap(Exception)
        mapper.canMap(Error)
    }

    def "maps Throwable with message to -32603"() {
        given:
        def ex = new RuntimeException("Something went wrong")

        when:
        def error = mapper.map(ex)

        then:
        error.jsonRpcError.code == -32603
        error.message == "Something went wrong"
    }

    def "maps Throwable with empty message using simple class name"() {
        given:
        def ex = new NullPointerException()

        when:
        def error = mapper.map(ex)

        then:
        error.jsonRpcError.code == -32603
        error.message == "NullPointerException"
    }
}
