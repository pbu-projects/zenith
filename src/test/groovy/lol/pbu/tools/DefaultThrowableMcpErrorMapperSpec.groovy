package lol.pbu.tools

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.serde.ObjectMapper
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
        def objectMapper = ObjectMapper.getDefault()
        def httpMapper = new HttpClientResponseExceptionMcpErrorMapper(objectMapper)
        def mapperWithDelegates = new DefaultThrowableMcpErrorMapper(httpMapper, null)

        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body('{"error":"RecordInvalid","description":"Validation failed"}')
        def httpEx = new HttpClientResponseException("Unprocessable", response)
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

    def "maps Throwable with empty message and cause chain by reporting class and causes"() {
        given:
        def root = new IOException("Connection refused")
        def intermediate = new IllegalStateException("", root)
        def ex = new RuntimeException("", intermediate)

        when:
        def error = mapper.map(ex)

        then:
        error.jsonRpcError.code == -32603
        error.message == "RuntimeException; Caused by: IllegalStateException; Caused by: IOException: Connection refused"
    }

    def "maps Throwable with primary message and cause chain"() {
        given:
        def cause = new IllegalArgumentException("Invalid token syntax")
        def ex = new RuntimeException("Authentication filter failure", cause)

        when:
        def error = mapper.map(ex)

        then:
        error.jsonRpcError.code == -32603
        error.message == "Authentication filter failure; Caused by: IllegalArgumentException: Invalid token syntax"
    }

    def "buildErrorMessage handles null input"() {
        expect:
        DefaultThrowableMcpErrorMapper.buildErrorMessage(null) == "Unknown error"
    }

    def "formatCauseChain handles null input"() {
        expect:
        DefaultThrowableMcpErrorMapper.formatCauseChain(null) == ""
    }

    def "buildErrorMessage handles cyclic cause chains without infinite loop"() {
        given:
        def cycleEx = new Exception("Self cyclic") {
            @Override
            Throwable getCause() {
                return this
            }
        }

        when:
        def msg = DefaultThrowableMcpErrorMapper.buildErrorMessage(cycleEx)

        then:
        msg.contains("Self cyclic")
    }

    def "buildErrorMessage handles mutually cyclic cause chains without infinite loop"() {
        given:
        def child = new Exception("Child error")
        def parent = new Exception("Parent error") {
            @Override
            Throwable getCause() {
                return child
            }
        }
        def dynamicChild = new Exception("Child error") {
            @Override
            Throwable getCause() {
                return parent
            }
        }

        when:
        def msg = DefaultThrowableMcpErrorMapper.buildErrorMessage(dynamicChild)

        then:
        msg.contains("Child error")
        msg.contains("Parent error")
    }
}

