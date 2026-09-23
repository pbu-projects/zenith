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
}
