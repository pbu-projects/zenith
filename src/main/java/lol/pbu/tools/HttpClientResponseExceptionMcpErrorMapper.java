package lol.pbu.tools;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.tree.JsonNode;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


/**
 * Maps HttpClientResponseException from Zendesk API calls into informative MCP errors.
 * Preserves Zendesk's diagnostic error messages (such as search syntax errors or unprocessable entity reasons).
 * Resolves issue #22.
 */
@Singleton
@Order(50)
public class HttpClientResponseExceptionMcpErrorMapper implements McpErrorExceptionMapper<HttpClientResponseException> {

    private static final Logger log = LoggerFactory.getLogger(HttpClientResponseExceptionMcpErrorMapper.class);
    private static final String KEY_DESCRIPTION = "description";

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
        log.error("Mapping HttpClientResponseException to MCP error: {}", formattedMessage, e);

        int mcpErrorCode = resolveMcpErrorCode(statusCode);
        return McpError.builder(mcpErrorCode)
                .message(formattedMessage)
                .build();
    }

    private String resolveFallbackDiagnosis(HttpClientResponseException e, int statusCode, String reason) {
        String exMsg = e.getMessage();
        String fallback;
        if (exMsg != null && exMsg.contains("The connector returned an error or an invalid response")) {
            fallback = switch (statusCode) {
                case 422 -> "The upstream Zendesk API rejected the request payload (HTTP 422 Unprocessable Entity). Check that the file extension matches the file content type, the file is not empty, and format is supported.";
                case 400 -> "The upstream Zendesk API rejected the request parameters (HTTP 400 Bad Request).";
                default -> "The upstream Zendesk API returned an error (" + statusCode + " " + reason + ").";
            };
        } else {
            fallback = (exMsg != null && !exMsg.isBlank()) ? exMsg : "The upstream Zendesk API returned HTTP " + statusCode + " " + reason;
        }

        if (e.getCause() != null) {
            String causeChain = DefaultThrowableMcpErrorMapper.formatCauseChain(e.getCause());
            if (!causeChain.isBlank()) {
                fallback = fallback + "; Caused by: " + causeChain;
            }
        }
        return fallback;
    }


    private int resolveStatusCode(HttpResponse<?> response, HttpClientResponseException e) {
        if (response != null) {
            return response.code();
        }
        return e.getStatus().getCode();
    }

    private String resolveReason(HttpResponse<?> response, HttpClientResponseException e) {
        if (response != null) {
            return response.reason();
        }
        return e.getStatus().getReason();
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
        Object rawBody = response.body();
        if (rawBody instanceof ByteBuffer<?> buf) {
            return buf.toString(StandardCharsets.UTF_8).trim();
        } else if (rawBody instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } else if (rawBody instanceof CharSequence cs) {
            return cs.toString().trim();
        } else if (rawBody instanceof JsonNode jn) {
            return jn.toString().trim();
        }
        Optional<String> bodyOpt = response.getBody(String.class);
        if (bodyOpt.isPresent()) {
            return bodyOpt.get().trim();
        }
        if (rawBody != null) {
            try {
                return objectMapper.writeValueAsString(rawBody).trim();
            } catch (Exception _) {
                return rawBody.toString().trim();
            }
        }
        return null;
    }

    private String parseJsonDiagnostic(String body) {
        try {
            JsonNode rootNode = objectMapper.readValue(body, JsonNode.class);
            if (rootNode == null || (!rootNode.isObject() && !rootNode.isArray())) {
                return null;
            }
            if (rootNode.isArray()) {
                return formatArrayErrors(rootNode);
            }
            StringBuilder sb = new StringBuilder();

            JsonNode errorNode = rootNode.get("error");
            if (errorNode != null && !errorNode.isNull()) {
                if (errorNode.isObject()) {
                    String title = readTextValue(errorNode.get("title"));
                    String msg = readTextValue(errorNode.get("message"));
                    String desc = readTextValue(errorNode.get(KEY_DESCRIPTION));
                    if (title != null) {
                        sb.append(title);
                    }
                    appendDiagnosticMessage(sb, title, desc != null ? errorNode.get(KEY_DESCRIPTION) : null, msg != null ? errorNode.get("message") : null);
                    JsonNode errDetails = errorNode.get("details");
                    if (errDetails != null && !errDetails.isNull()) {
                        appendWithSeparator(sb, ": ", formatDetails(errDetails));
                    }
                } else {
                    String error = readTextValue(errorNode);
                    if (error != null) {
                        sb.append(error);
                    }
                }
            }

            JsonNode errorsNode = rootNode.get("errors");
            if (errorsNode != null && !errorsNode.isNull()) {
                String formattedErrors = errorsNode.isArray() ? formatArrayErrors(errorsNode) : formatDetails(errorsNode);
                if (formattedErrors != null && !formattedErrors.isBlank()) {
                    appendWithSeparator(sb, " - ", formattedErrors);
                }
            }

            String currentError = !sb.isEmpty() ? sb.toString() : null;
            appendDiagnosticMessage(sb, currentError, rootNode.get(KEY_DESCRIPTION), rootNode.get("message"));

            JsonNode detailsNode = rootNode.get("details");
            if (detailsNode != null && !detailsNode.isNull()) {
                appendWithSeparator(sb, ": ", formatDetails(detailsNode));
            }
            return !sb.isEmpty() ? sb.toString() : null;
        } catch (Exception parseEx) {
            log.debug("Could not parse Zendesk error response body as JSON: {}", parseEx.getMessage());
            return null;
        }
    }

    private String formatArrayErrors(JsonNode arrayNode) {
        List<String> items = new ArrayList<>();
        for (JsonNode item : arrayNode.values()) {
            if (item == null || item.isNull()) continue;
            if (item.isObject()) {
                StringBuilder itemSb = new StringBuilder();
                String title = readTextValue(item.get("title"));
                String code = readTextValue(item.get("code"));
                String err = readTextValue(item.get("error"));
                String header = title != null ? title : (code != null ? code : err);
                if (header != null) {
                    itemSb.append(header);
                }

                String desc = readTextValue(item.get(KEY_DESCRIPTION));
                String msg = readTextValue(item.get("message"));
                String detail = desc != null ? desc : msg;
                if (detail != null && !detail.equals(header)) {
                    appendWithSeparator(itemSb, ": ", detail);
                }

                JsonNode itemDetails = item.get("details");
                if (itemDetails != null && !itemDetails.isNull()) {
                    appendWithSeparator(itemSb, " - ", formatDetails(itemDetails));
                }
                if (!itemSb.isEmpty()) {
                    items.add(itemSb.toString());
                }
            } else {
                String text = item.coerceStringValue();
                if (text != null && !text.isBlank()) {
                    items.add(text);
                }
            }
        }
        return String.join("; ", items);
    }

    private void appendDiagnosticMessage(StringBuilder sb, String error, JsonNode descNode, JsonNode msgNode) {
        String desc = readTextValue(descNode);
        if (desc != null) {
            appendWithSeparator(sb, " - ", desc);
            return;
        }
        String msg = readTextValue(msgNode);
        if (msg != null && !msg.equals(error)) {
            appendWithSeparator(sb, " - ", msg);
        }
    }

    private void appendWithSeparator(StringBuilder sb, String separator, String text) {
        if (!sb.isEmpty()) {
            sb.append(separator);
        }
        sb.append(text);
    }

    private String readTextValue(JsonNode node) {
        if (node != null && !node.isNull()) {
            String val = node.coerceStringValue();
            if (!val.isBlank()) {
                return val;
            }
        }
        return null;
    }

    private String formatDetails(JsonNode detailsNode) {
        if (!detailsNode.isObject()) {
            return detailsNode.coerceStringValue();
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : detailsNode.entries()) {
            String key = entry.getKey();
            entries.add(key + ": " + formatDetailValue(entry.getValue()));
        }
        return String.join("; ", entries);
    }

    private String formatDetailValue(JsonNode val) {
        if (val.isArray()) {
            return StreamSupport.stream(val.values().spliterator(), false)
                    .map(this::extractItemDescription)
                    .collect(Collectors.joining(", "));
        }
        return extractItemDescription(val);
    }

    private String extractItemDescription(JsonNode item) {
        if (item != null && item.isObject()) {
            JsonNode desc = item.get(KEY_DESCRIPTION);
            if (desc != null && !desc.isNull()) {
                return desc.coerceStringValue();
            }
        }
        return item != null ? item.coerceStringValue() : "";
    }
}
