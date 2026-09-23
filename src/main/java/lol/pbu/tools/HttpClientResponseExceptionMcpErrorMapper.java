package lol.pbu.tools;

import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.micronaut.serde.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Maps HttpClientResponseException from Zendesk API calls into informative MCP errors.
 * Preserves Zendesk's diagnostic error messages (such as search syntax errors or unprocessable entity reasons).
 * Resolves issue #22.
 */
@Singleton
@Order(50)
public class HttpClientResponseExceptionMcpErrorMapper implements McpErrorExceptionMapper<HttpClientResponseException> {

    private static final Logger log = LoggerFactory.getLogger(HttpClientResponseExceptionMcpErrorMapper.class);

    private final ObjectMapper objectMapper;

    @Inject
    public HttpClientResponseExceptionMcpErrorMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canMap(Class<? extends Throwable> c) {
        return HttpClientResponseException.class.isAssignableFrom(c);
    }

    @Override
    public McpError map(HttpClientResponseException e) {
        HttpResponse<?> response = e.getResponse();
        int statusCode = response != null ? response.code() : (e.getStatus() != null ? e.getStatus().getCode() : 500);
        String reason = response != null ? response.reason() : (e.getStatus() != null ? e.getStatus().getReason() : "Internal Error");

        String diagnosis = extractZendeskErrorMessage(response);
        if (diagnosis == null || diagnosis.isBlank()) {
            diagnosis = e.getMessage();
        }

        String formattedMessage = String.format("Zendesk API error (HTTP %d %s): %s", statusCode, reason, diagnosis);
        log.warn("Mapping HttpClientResponseException to MCP error: {}", formattedMessage);

        int mcpErrorCode = (statusCode >= 400 && statusCode < 500) ? -32602 : -32603;
        return McpError.builder(mcpErrorCode)
                .message(formattedMessage)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractZendeskErrorMessage(HttpResponse<?> response) {
        if (response == null) {
            return null;
        }
        Optional<String> bodyOpt = response.getBody(String.class);
        if (bodyOpt.isEmpty()) {
            return null;
        }
        String body = bodyOpt.get().trim();
        if (body.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            if (map != null) {
                Object errorObj = map.get("error");
                Object descObj = map.get("description");
                Object msgObj = map.get("message");
                Object detailsObj = map.get("details");

                StringBuilder sb = new StringBuilder();
                if (errorObj != null) {
                    sb.append(errorObj);
                }
                if (descObj != null) {
                    if (sb.length() > 0) {
                        sb.append(" - ");
                    }
                    sb.append(descObj);
                } else if (msgObj != null && !msgObj.equals(errorObj)) {
                    if (sb.length() > 0) {
                        sb.append(" - ");
                    }
                    sb.append(msgObj);
                }
                if (detailsObj != null) {
                    if (sb.length() > 0) {
                        sb.append(": ");
                    }
                    sb.append(detailsObj);
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
        } catch (Exception parseEx) {
            log.debug("Could not parse Zendesk error response body as JSON: {}", parseEx.getMessage());
        }
        return body;
    }
}
