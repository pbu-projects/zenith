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

        // Explicit handling for HTTP 429 (Too Many Requests / Rate Limited)
        if (statusCode == 429) {
            String retryAfter = response != null ? response.header("Retry-After") : null;
            String resetSeconds = response != null ? response.header("ratelimit-reset") : null;
            String waitTime = (retryAfter != null && !retryAfter.isBlank()) ? retryAfter.trim() : (resetSeconds != null ? resetSeconds.trim() : null);

            String rateLimitMsg;
            if (waitTime != null && !waitTime.isBlank()) {
                boolean isNumeric = waitTime.chars().allMatch(Character::isDigit);
                rateLimitMsg = isNumeric
                        ? String.format("Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted. You must wait %s seconds before sending further requests.", waitTime)
                        : String.format("Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted. Retry after: %s.", waitTime);
            } else {
                rateLimitMsg = "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted. Please pause before retrying.";
            }

            log.warn("Mapping HTTP 429 Rate Limit to MCP error: {}", rateLimitMsg);

            // Use -32029 (within JSON-RPC reserved server-error range -32000 to -32099)
            // so LLM agents recognize this as an upstream rate limit and DO NOT treat it as invalid arguments (-32602).
            return McpError.builder(-32029)
                    .message(rateLimitMsg)
                    .build();
        }

        String diagnosis = extractZendeskErrorMessage(response);
        if (diagnosis == null || diagnosis.isBlank()) {
            diagnosis = e.getMessage();
        }

        String formattedMessage = String.format("Zendesk API error (HTTP %d %s): %s", statusCode, reason, diagnosis);
        log.warn("Mapping HttpClientResponseException to MCP error: {}", formattedMessage);

        // Differentiate client argument errors (400, 422) from auth/server errors (401, 403, 5xx)
        int mcpErrorCode;
        if (statusCode == 400 || statusCode == 422) {
            mcpErrorCode = -32602; // Invalid params
        } else if (statusCode == 404) {
            mcpErrorCode = -32002; // Resource Not Found
        } else {
            mcpErrorCode = -32603; // Internal / Upstream Error
        }

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

        if (body.startsWith("<") || body.contains("<html") || body.contains("<HTML")) {
            return "[Non-JSON HTML error page received from gateway]";
        }
        if (body.length() > 500) {
            return body.substring(0, 500) + "... [truncated]";
        }
        return body;
    }
}
