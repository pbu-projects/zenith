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

    def "unwraps causal chain and delegates to HttpClientResponseExceptionMcpErrorMapper"() {
        given:
        def objectMapper = io.micronaut.serde.ObjectMapper.getDefault()
        def httpMapper = new HttpClientResponseExceptionMcpErrorMapper(objectMapper)
        def mapperWithDelegates = new DefaultThrowableMcpErrorMapper(httpMapper, null)

        def response = io.micronaut.http.HttpResponse.status(io.micronaut.http.HttpStatus.UNPROCESSABLE_ENTITY)
                .body('{"error":"RecordInvalid","description":"Validation failed"}')
        def httpEx = new io.micronaut.http.client.exceptions.HttpClientResponseException("Unprocessable", response)
        def wrappedEx = new RuntimeException("Outer wrapper", new IllegalStateException("Middle wrapper", httpEx))

        when:
        def error = mapperWithDelegates.map(wrappedEx)

        then:
        error.jsonRpcError.code == -32602
        error.message.contains("Zendesk API error (HTTP 422 Unprocessable Entity)")
        error.message.contains("RecordInvalid - Validation failed")
    }

    def "unwraps causal chain and delegates to IllegalArgumentExceptionMcpErrorMapper"() {
        given:
        def argMapper = new IllegalArgumentExceptionMcpErrorMapper()
        def mapperWithDelegates = new DefaultThrowableMcpErrorMapper(null, argMapper)

        def argEx = new IllegalArgumentException("File not found at path: /invalid/path.txt")
        def wrappedEx = new RuntimeException("Upload failed", argEx)

        when:
        def error = mapperWithDelegates.map(wrappedEx)

        then:
        error.jsonRpcError.code == -32602
        error.message == "File not found at path: /invalid/path.txt"
    }
}
