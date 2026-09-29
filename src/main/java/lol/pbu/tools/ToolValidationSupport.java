package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ToolValidationSupport {

    public static final String PARAM_CUSTOM_STATUS_ID_SNAKE = "custom_status_id";
    public static final String PARAM_TICKET_FORM_ID_SNAKE = "ticket_form_id";
    public static final String PARAM_TICKET_FORM_ID_CAMEL = "ticketFormId";

    private ToolValidationSupport() {}

    public static void validateKnownParameters(CallToolRequest request, String toolName, String... knownParams) {
        if (request == null || request.arguments() == null) return;
        Set<String> known = new HashSet<>(Arrays.asList(knownParams));
        if (request.arguments().containsKey("description") && !known.contains("description")) {
            throw new IllegalArgumentException("Ticket description cannot be modified after creation as it is read-only in Zendesk. To add information to an existing ticket, use the 'comment' parameter instead.");
        }
        for (String key : request.arguments().keySet()) {
            if (!known.contains(key)) {
                String hint;
                if ("uploadAttachment".equals(toolName)) {
                    hint = "Valid parameters for uploadAttachment are 'filePath' and 'filename'.";
                } else if ("getTicketAudits".equals(toolName)) {
                    hint = "The only valid parameter for getTicketAudits is 'ticketId'.";
                } else if ("createTicket".equals(toolName)) {
                    hint = "If you meant to set a custom field, use the 'customFields' array parameter.";
                } else {
                    hint = "If you meant to update a custom field, use the 'customFields' array parameter.";
                }
                throw new IllegalArgumentException("Unrecognized parameter: '" + key + "'. " + hint);
            }
        }
    }

    public static Long resolveCustomStatusId(Long customStatusId, @Nullable CallToolRequest request) {
        if (customStatusId != null) {
            return customStatusId;
        }
        if (request != null && request.arguments() != null && request.arguments().containsKey(PARAM_CUSTOM_STATUS_ID_SNAKE)) {
            return parseLongArgument(request.arguments().get(PARAM_CUSTOM_STATUS_ID_SNAKE), PARAM_CUSTOM_STATUS_ID_SNAKE);
        }
        return null;
    }

    public static void validateCustomStatusBounds(Long customStatusId) {
        if (customStatusId == null) {
            return;
        }
        if (customStatusId <= 0) {
            throw new IllegalArgumentException("customStatusId must be a positive integer, got: " + customStatusId);
        }
        if (customStatusId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("customStatusId " + customStatusId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits custom_status_id on ticket inputs to 32-bit integers.");
        }
    }

    public static Long resolveTicketFormId(Long ticketFormId, @Nullable CallToolRequest request) {
        if (ticketFormId != null) {
            return ticketFormId;
        }
        if (request != null && request.arguments() != null) {
            if (request.arguments().containsKey(PARAM_TICKET_FORM_ID_SNAKE)) {
                return parseLongArgument(request.arguments().get(PARAM_TICKET_FORM_ID_SNAKE), PARAM_TICKET_FORM_ID_SNAKE);
            }
            if (request.arguments().containsKey(PARAM_TICKET_FORM_ID_CAMEL)) {
                return parseLongArgument(request.arguments().get(PARAM_TICKET_FORM_ID_CAMEL), PARAM_TICKET_FORM_ID_CAMEL);
            }
        }
        return null;
    }

    private static Long parseLongArgument(Object val, String paramName) {
        if (val instanceof Number num) {
            return num.longValue();
        } else if (val != null) {
            try {
                return Long.parseLong(val.toString().trim());
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(paramName + " must be a numeric ID, got: " + val);
            }
        }
        return null;
    }

    public static void validateTicketFormBounds(Long ticketFormId) {
        if (ticketFormId == null) return;
        if (ticketFormId <= 0) {
            throw new IllegalArgumentException("ticketFormId must be a positive integer, got: " + ticketFormId);
        }
    }
}
