package lol.pbu.tools

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

class HttpClientResponseExceptionMcpErrorMapperSpec extends Specification {

    ObjectMapper objectMapper = ObjectMapper.getDefault()
    HttpClientResponseExceptionMcpErrorMapper mapper = new HttpClientResponseExceptionMcpErrorMapper(objectMapper)

    def "maps HTTP 429 to -32029 with Retry-After wait guidance"() {
        given:
        def response = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "45")
                .body('{"error":"TooManyRequests","description":"Rate limit exceeded"}')
        def ex = new HttpClientResponseException("Too Many Requests", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32029
        mcpError.message.contains("Zendesk API rate limit exceeded (HTTP 429 Too Many Requests)")
        mcpError.message.contains("45 seconds")
    }

    def "maps HTTP 400 Bad Request to -32602 with Zendesk error diagnosis"() {
        given:
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST)
                .body('{"error":"invalid_query","description":"Invalid search syntax"}')
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("HTTP 400 Bad Request")
        mcpError.message.contains("invalid_query - Invalid search syntax")
    }

    def "maps HTTP 404 to -32002 Resource Not Found"() {
        given:
        def response = HttpResponse.status(HttpStatus.NOT_FOUND)
                .body('{"error":"RecordNotFound","description":"Not found"}')
        def ex = new HttpClientResponseException("Not Found", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32002
        mcpError.message.contains("RecordNotFound")
    }

    def "maps HTTP 401 Unauthorized to -32603 Server Error"() {
        given:
        def response = HttpResponse.status(HttpStatus.UNAUTHORIZED)
                .body('{"error":"Couldn\'t authenticate you"}')
        def ex = new HttpClientResponseException("Unauthorized", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.contains("Couldn't authenticate you")
    }

    def "handles HTTP-date Retry-After header gracefully"() {
        given:
        def response = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "Fri, 31 Dec 2026 23:59:59 GMT")
        def ex = new HttpClientResponseException("Too Many Requests", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32029
        mcpError.message.contains("Retry after: Fri, 31 Dec 2026 23:59:59 GMT")
        !mcpError.message.contains("seconds")
    }

    def "truncates HTML and large error bodies to protect token limits"() {
        given:
        def htmlBody = "<html><head><title>502 Bad Gateway</title></head><body><h1>Bad Gateway</h1><p>" + ("x" * 1000) + "</p></body></html>"
        def response = HttpResponse.status(HttpStatus.BAD_GATEWAY)
                .body(htmlBody)
        def ex = new HttpClientResponseException("Bad Gateway", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.contains("[Non-JSON HTML error page received from gateway]")
        !mcpError.message.contains("<html>")
    }

    def "truncates excessively large JSON error diagnostics to protect context window"() {
        given:
        def hugeDetails = "A" * 1000
        def jsonBody = '{"error":"RecordInvalid","description":"Validation failed","details":"' + hugeDetails + '"}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(jsonBody)
        def ex = new HttpClientResponseException("Unprocessable Entity", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("... [truncated]")
        mcpError.message.length() < 600
    }

    def "handles ratelimit-reset header when Retry-After is absent"() {
        given:
        def response = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("ratelimit-reset", "30")
        def ex = new HttpClientResponseException("Too Many Requests", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32029
        mcpError.message.contains("30 seconds")
    }

    def "handles HTTP 429 without rate limit headers"() {
        given:
        def response = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
        def ex = new HttpClientResponseException("Too Many Requests", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32029
        mcpError.message.contains("Please pause before retrying")
    }

    def "formats JSON error diagnostics with message field and empty body fallback"() {
        given:
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST)
                .body('{"error":"Forbidden","message":"Access Denied"}')
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("Forbidden - Access Denied")

        when: "empty body"
        def emptyEx = new HttpClientResponseException("Empty Bad Request", HttpResponse.status(HttpStatus.BAD_REQUEST).body(""))
        def emptyMcpError = mapper.map(emptyEx)

        then:
        emptyMcpError.message.contains("Empty Bad Request")
    }

    def "formats structured validation error details from Zendesk upload endpoint"() {
        given:
        def json = '{"error":"RecordInvalid","description":"Record validation errors","details":{"base":[{"description":"The file type and file extension do not match. Try again."}]}}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(json)
        def ex = new HttpClientResponseException("Unprocessable Entity", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("Zendesk API error (HTTP 422 Unprocessable Entity)")
        mcpError.message.contains("RecordInvalid - Record validation errors: base: The file type and file extension do not match. Try again.")
    }

    def "handles AttachmentUnprocessable upload failure"() {
        given:
        def json = '{"error":"AttachmentUnprocessable","description":"Attachment file could not be processed."}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(json)
        def ex = new HttpClientResponseException("Unprocessable Entity", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("AttachmentUnprocessable - Attachment file could not be processed.")
    }

    def "replaces generic connector error with actionable guidance on HTTP 422 and 400"() {
        given: "HTTP 422 with generic connector error message and empty body"
        def response422 = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body("")
        def ex422 = new HttpClientResponseException("Client ':zendesk': The connector returned an error or an invalid response", response422)

        when:
        def err422 = mapper.map(ex422)

        then:
        err422.jsonRpcError.code == -32602
        err422.message.contains("The upstream Zendesk API rejected the request payload (HTTP 422 Unprocessable Entity)")
        err422.message.contains("Check that the file extension matches the file content type")

        when: "HTTP 400 with generic connector error message and empty body"
        def response400 = HttpResponse.status(HttpStatus.BAD_REQUEST).body("")
        def ex400 = new HttpClientResponseException("The connector returned an error or an invalid response", response400)
        def err400 = mapper.map(ex400)

        then:
        err400.jsonRpcError.code == -32602
        err400.message.contains("The upstream Zendesk API rejected the request parameters (HTTP 400 Bad Request)")
    }

    def "extracts error diagnosis from byte[] raw response body"() {
        given:
        byte[] bytes = '{"error":"InvalidData","description":"Byte payload error"}'.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(bytes)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("InvalidData - Byte payload error")
    }

    def "maps HTTP 413 Payload Too Large to -32602 Invalid params"() {
        given:
        def response = HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE).body('{"error":"AttachmentTooLarge","description":"Exceeded 50MB"}')
        def ex = new HttpClientResponseException("Payload Too Large", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("AttachmentTooLarge - Exceeded 50MB")
    }

    def "handles HTTP 503 Service Unavailable gracefully"() {
        given:
        def ex = new HttpClientResponseException("Service Unavailable", HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE))

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.contains("HTTP 503 Service Unavailable")
        mcpError.message.contains("Service Unavailable")
    }

    def "replaces generic connector error message with status 500 upstream notice"() {
        given:
        def response500 = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body("")
        def ex500 = new HttpClientResponseException("The connector returned an error or an invalid response", response500)

        when:
        def err500 = mapper.map(ex500)

        then:
        err500.jsonRpcError.code == -32603
        err500.message.contains("The upstream Zendesk API returned an error (500 Internal Server Error)")
    }

    def "formats details when details map contains a nested map with description and primitives"() {
        given:
        def json = '{"error":"RecordInvalid","description":"Validation failed","details":{"field1":{"description":"Invalid field value"},"field2":"Invalid simple value"}}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new StringBuilder(json))
        def ex = new HttpClientResponseException("Unprocessable", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("field1: Invalid field value")
        mcpError.message.contains("field2: Invalid simple value")
    }
}

