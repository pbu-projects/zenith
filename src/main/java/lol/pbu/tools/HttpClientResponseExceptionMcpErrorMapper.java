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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        int statusCode = resolveStatusCode(response, e);
        String reason = resolveReason(response, e);

        // Explicit handling for HTTP 429 (Too Many Requests / Rate Limited)
        if (statusCode == 429) {
            return handleRateLimit(response);
        }

        String diagnosis = extractZendeskErrorMessage(response);
        if (diagnosis == null || diagnosis.isBlank()) {
            diagnosis = resolveFallbackDiagnosis(e, statusCode, reason);
        }

        String formattedMessage = String.format("Zendesk API error (HTTP %d %s): %s", statusCode, reason, diagnosis);
        log.warn("Mapping HttpClientResponseException to MCP error: {}", formattedMessage);

        int mcpErrorCode = resolveMcpErrorCode(statusCode);
        return McpError.builder(mcpErrorCode)
                .message(formattedMessage)
                .build();
    }

    private String resolveFallbackDiagnosis(HttpClientResponseException e, int statusCode, String reason) {
        String exMsg = e.getMessage();
        if (exMsg != null && exMsg.contains("The connector returned an error or an invalid response")) {
            if (statusCode == 422) {
                return "The upstream Zendesk API rejected the request payload (HTTP 422 Unprocessable Entity). Check that the file extension matches the file content type, the file is not empty, and format is supported.";
            } else if (statusCode == 400) {
                return "The upstream Zendesk API rejected the request parameters (HTTP 400 Bad Request).";
            } else {
                return "The upstream Zendesk API returned an error (" + statusCode + " " + reason + ").";
            }
        }
        return (exMsg != null && !exMsg.isBlank()) ? exMsg : "The upstream Zendesk API returned HTTP " + statusCode + " " + reason;
    }

    private int resolveStatusCode(HttpResponse<?> response, HttpClientResponseException e) {
        if (response != null) {
            return response.code();
        }
        if (e.getStatus() != null) {
            return e.getStatus().getCode();
        }
        return 500;
    }

    private String resolveReason(HttpResponse<?> response, HttpClientResponseException e) {
        if (response != null) {
            return response.reason();
        }
        if (e.getStatus() != null) {
            return e.getStatus().getReason();
        }
        return "Internal Error";
    }

    private int resolveMcpErrorCode(int statusCode) {
        return switch (statusCode) {
            case 400, 422, 413 -> -32602; // Invalid params
            case 404 -> -32002;      // Resource Not Found
            default -> -32603;       // Internal / Upstream Error
        };
    }

    private McpError handleRateLimit(HttpResponse<?> response) {
        String waitTime = resolveWaitTime(response);
        String rateLimitMsg = formatRateLimitMessage(waitTime);
        log.warn("Mapping HTTP 429 Rate Limit to MCP error: {}", rateLimitMsg);

        // Use -32029 (within JSON-RPC reserved server-error range -32000 to -32099)
        // so LLM agents recognize this as an upstream rate limit and DO NOT treat it as invalid arguments (-32602).
        return McpError.builder(-32029)
                .message(rateLimitMsg)
                .build();
    }

    private String resolveWaitTime(HttpResponse<?> response) {
        if (response == null) {
            return null;
        }
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isBlank()) {
            return retryAfter.trim();
        }
        String resetSeconds = response.header("ratelimit-reset");
        if (resetSeconds != null && !resetSeconds.isBlank()) {
            return resetSeconds.trim();
        }
        return null;
    }

    private String formatRateLimitMessage(String waitTime) {
        if (waitTime == null || waitTime.isBlank()) {
            return "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted. Please pause before retrying.";
        }
        boolean isNumeric = waitTime.chars().allMatch(Character::isDigit);
        String template = isNumeric
                ? "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted. You must wait %s seconds before sending further requests."
                : "Zendesk API rate limit exceeded (HTTP 429 Too Many Requests). Automatic retries exhausted. Retry after: %s.";
        return String.format(template, waitTime);
    }

    private String extractZendeskErrorMessage(HttpResponse<?> response) {
        if (response == null) {
            return null;
        }
        String body = resolveResponseBody(response);
        if (body == null || body.isEmpty()) {
            return null;
        }

        String jsonDiagnostic = parseJsonDiagnostic(body);
        String diagnostic = jsonDiagnostic;
        if (diagnostic == null) {
            if (body.startsWith("<") || body.contains("<html") || body.contains("<HTML")) {
                return "[Non-JSON HTML error page received from gateway]";
            }
            diagnostic = body;
        }

        if (diagnostic.length() > 500) {
            return diagnostic.substring(0, 500) + "... [truncated]";
        }
        return diagnostic;
    }

    private String resolveResponseBody(HttpResponse<?> response) {
        Optional<String> bodyOpt = response.getBody(String.class);
        if (bodyOpt.isPresent()) {
            return bodyOpt.get().trim();
        }
        Object rawBody = response.body();
        if (rawBody instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } else if (rawBody instanceof CharSequence cs) {
            return cs.toString().trim();
        } else if (rawBody != null) {
            try {
                return objectMapper.writeValueAsString(rawBody).trim();
            } catch (Exception _) {
                return rawBody.toString().trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String parseJsonDiagnostic(String body) {
        try {
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            if (map == null) {
                return null;
            }
            Object errorObj = map.get("error");
            Object descObj = map.get("description");
            Object msgObj = map.get("message");
            Object detailsObj = map.get("details");

            StringBuilder sb = new StringBuilder();
            if (errorObj != null) {
                sb.append(errorObj);
            }
            if (descObj != null) {
                if (!sb.isEmpty()) {
                    sb.append(" - ");
                }
                sb.append(descObj);
            } else if (msgObj != null && !msgObj.equals(errorObj)) {
                if (!sb.isEmpty()) {
                    sb.append(" - ");
                }
                sb.append(msgObj);
            }
            if (detailsObj != null) {
                if (!sb.isEmpty()) {
                    sb.append(": ");
                }
                sb.append(formatDetails(detailsObj));
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        } catch (Exception parseEx) {
            log.debug("Could not parse Zendesk error response body as JSON: {}", parseEx.getMessage());
        }
        return null;
    }

    private String formatDetails(Object detailsObj) {
        if (detailsObj instanceof Map<?, ?> detailsMap) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : detailsMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object val = entry.getValue();
                if (val instanceof List<?> valList) {
                    List<String> listItems = new ArrayList<>();
                    for (Object item : valList) {
                        if (item instanceof Map<?, ?> itemMap && itemMap.containsKey("description")) {
                            listItems.add(String.valueOf(itemMap.get("description")));
                        } else {
                            listItems.add(String.valueOf(item));
                        }
                    }
                    entries.add(key + ": " + String.join(", ", listItems));
                } else if (val instanceof Map<?, ?> valMap && valMap.containsKey("description")) {
                    entries.add(key + ": " + valMap.get("description"));
                } else {
                    entries.add(key + ": " + val);
                }
            }
            return String.join("; ", entries);
        }
        return detailsObj.toString();
    }
}
