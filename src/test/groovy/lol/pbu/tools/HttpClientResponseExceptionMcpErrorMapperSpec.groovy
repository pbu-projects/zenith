package lol.pbu.tools

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import java.nio.charset.StandardCharsets
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
        byte[] bytes = '{"error":"InvalidData","description":"Byte payload error"}'.getBytes(StandardCharsets.UTF_8)
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

    def "includes cause chain in fallback diagnosis when cause is present"() {
        given:
        def cause = new IOException("Premature EOF in HTTP stream")
        def ex = new HttpClientResponseException("The connector returned an error or an invalid response", cause, HttpResponse.status(HttpStatus.OK))

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.toLowerCase().contains("the upstream zendesk api returned an error (200 ok)")
        mcpError.message.contains("Caused by: IOException: Premature EOF in HTTP stream")
    }

    def "handles cyclic cause chains gracefully in fallback diagnosis"() {
        given:
        def cycleCause = new IOException("Cyclic network error") {
            @Override
            Throwable getCause() {
                return this
            }
        }
        def ex = new HttpClientResponseException("The connector returned an error or an invalid response", cycleCause, HttpResponse.status(HttpStatus.OK))

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.contains("Cyclic network error")
    }

    def "extracts error diagnosis from ByteBuffer raw response body"() {
        given:
        byte[] bytes = '{"error":"InvalidData","description":"ByteBuffer payload error"}'.getBytes(StandardCharsets.UTF_8)
        def buffer = ByteArrayBufferFactory.INSTANCE.wrap(bytes)
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(buffer)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("InvalidData - ByteBuffer payload error")
    }

    def "formats structured error diagnosis when error field is a JSON object"() {
        given:
        def json = '{"error":{"title":"InvalidAttribute","message":"Record validation errors","details":{"requester":[{"description":"Requester 382716491823 is not a valid user"}]}}}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(json)
        def ex = new HttpClientResponseException("Unprocessable Entity", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("InvalidAttribute - Record validation errors: requester: Requester 382716491823 is not a valid user")
    }

    def "formats structured error diagnosis when errors field is an array of objects"() {
        given:
        def json = '{"errors":[{"title":"InvalidAttribute","message":"Requester is invalid"}]}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(json)
        def ex = new HttpClientResponseException("Unprocessable Entity", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("InvalidAttribute: Requester is invalid")
    }

    def "formats structured error diagnosis when root is an array of error messages"() {
        given:
        def json = '["Custom status 1000000000002 not found", "Requester ID invalid"]'
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(json)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("Custom status 1000000000002 not found; Requester ID invalid")
    }

    def "canMap correctly identifies supported exception types"() {
        expect:
        mapper.canMap(HttpClientResponseException)
        !mapper.canMap(IllegalArgumentException)
        !mapper.canMap(RuntimeException)
    }

    def "extracts error diagnosis from byte array raw response body"() {
        given:
        byte[] bytes = '{"error":"ByteArrayError","description":"Raw byte array payload"}'.getBytes(StandardCharsets.UTF_8)
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(bytes)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("ByteArrayError - Raw byte array payload")
    }

    def "extracts error diagnosis from CharSequence raw response body"() {
        given:
        CharSequence seq = new StringBuilder('{"error":"CharSequenceError","description":"StringBuilder payload"}')
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(seq)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("CharSequenceError - StringBuilder payload")
    }

    def "extracts error diagnosis from JsonNode raw response body"() {
        given:
        JsonNode node = objectMapper.readValue('{"error":"JsonNodeError","description":"Parsed tree payload"}', JsonNode)
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(node)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("JsonNodeError - Parsed tree payload")
    }

    def "extracts error diagnosis from arbitrary POJO / Map serialized via objectMapper"() {
        given:
        def payloadMap = [error: "PojoError", description: "Serialized POJO map payload"]
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(payloadMap)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("PojoError - Serialized POJO map payload")
    }

    def "falls back to toString when body object serialization fails"() {
        given:
        def failingBody = new Object() {
            @Override
            String toString() {
                return 'Unserializable plain text body'
            }
        }
        def response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failingBody)
        def ex = new HttpClientResponseException("Server Error", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.contains("Unserializable plain text body")
    }

    def "handles JSON primitive root and falls back gracefully"() {
        given:
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body("12345")
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("12345")
    }

    def "formats error object with title and details"() {
        given:
        def json = '{"error":{"title":"InvalidField","details":{"email":"is invalid"}}}'
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(json)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("InvalidField: email: is invalid")
    }

    def "formats error object with message when title is null"() {
        given:
        def json = '{"error":{"message":"Only message provided"}}'
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(json)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("Only message provided")
    }

    def "formats errors object when errors is a JSON object instead of array"() {
        given:
        def json = '{"errors":{"field":[{"description":"field is required"}]}}'
        def response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(json)
        def ex = new HttpClientResponseException("Unprocessable Entity", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("field: field is required")
    }

    def "formats root message and description when error field is absent"() {
        given:
        def jsonDesc = '{"description":"Root description","details":{"code":"1001"}}'
        def responseDesc = HttpResponse.status(HttpStatus.BAD_REQUEST).body(jsonDesc)
        def exDesc = new HttpClientResponseException("Bad Request", responseDesc)

        when:
        def mcpErrorDesc = mapper.map(exDesc)

        then:
        mcpErrorDesc.jsonRpcError.code == -32602
        mcpErrorDesc.message.contains("Root description: code: 1001")

        when: "only message is present on root"
        def jsonMsg = '{"message":"Root message only"}'
        def responseMsg = HttpResponse.status(HttpStatus.BAD_REQUEST).body(jsonMsg)
        def exMsg = new HttpClientResponseException("Bad Request", responseMsg)
        def mcpErrorMsg = mapper.map(exMsg)

        then:
        mcpErrorMsg.jsonRpcError.code == -32602
        mcpErrorMsg.message.contains("Root message only")
    }

    def "formats array errors with code, error, details, and null elements"() {
        given:
        def json = '''[
            null,
            {},
            {"code":"DuplicateCode","message":"Already exists"},
            {"error":"ErrorField","message":"Something wrong"},
            {"title":"DetailsField","details":{"info":[{"description":"Detailed info"}]}}
        ]'''
        def response = HttpResponse.status(HttpStatus.BAD_REQUEST).body(json)
        def ex = new HttpClientResponseException("Bad Request", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32602
        mcpError.message.contains("DuplicateCode: Already exists")
        mcpError.message.contains("ErrorField: Something wrong")
        mcpError.message.contains("DetailsField - info: Detailed info")
    }

    def "handles non-JSON plain text body without HTML"() {
        given:
        def response = HttpResponse.status(HttpStatus.BAD_GATEWAY).body("Plain text upstream error")
        def ex = new HttpClientResponseException("Bad Gateway", response)

        when:
        def mcpError = mapper.map(ex)

        then:
        mcpError.jsonRpcError.code == -32603
        mcpError.message.contains("Plain text upstream error")
    }
}




