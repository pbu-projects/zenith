package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ToolValidationSupport {

    public static final String PARAM_CUSTOM_STATUS_ID_SNAKE = "custom_status_id";
    public static final String PARAM_TICKET_FORM_ID_SNAKE = "ticket_form_id";
    public static final String PARAM_TICKET_FORM_ID_CAMEL = "ticketFormId";
    public static final String PARAM_ADDITIONAL_TAGS_CAMEL = "additionalTags";
    public static final String PARAM_ADDITIONAL_TAGS_SNAKE = "additional_tags";
    public static final String PARAM_REMOVE_TAGS_CAMEL = "removeTags";
    public static final String PARAM_REMOVE_TAGS_SNAKE = "remove_tags";
    public static final String PARAM_TAGS = "tags";

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
                } else if (toolName != null && toolName.contains("CustomObject")) {
                    hint = "Check the tool documentation for valid parameters.";
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

    @Nullable
    @SuppressWarnings("java:S1168") // null return indicates parameter was omitted, whereas empty list indicates clearing tags
    public static List<String> resolveAndValidateTags(
            @Nullable List<String> explicitTags,
            String camelKey,
            @Nullable String snakeKey,
            @Nullable CallToolRequest request
    ) {
        Object raw = extractRawTags(explicitTags, camelKey, snakeKey, request);
        if (raw == null) {
            return null;
        }
        Collection<?> items = switch (raw) {
            case Collection<?> coll -> coll;
            case Object[] arr -> Arrays.asList(arr);
            default -> throw new IllegalArgumentException(camelKey + " must be a list of strings, got: " + raw.getClass().getSimpleName());
        };
        return items.stream()
                .map(item -> validateTagItem(item, camelKey))
                .toList();
    }

    private static Object extractRawTags(
            @Nullable List<String> explicitTags,
            String camelKey,
            @Nullable String snakeKey,
            @Nullable CallToolRequest request
    ) {
        if (explicitTags != null) {
            return explicitTags;
        }
        if (request == null || request.arguments() == null) {
            return null;
        }
        if (snakeKey != null && request.arguments().containsKey(snakeKey)) {
            return request.arguments().get(snakeKey);
        }
        if (camelKey != null && request.arguments().containsKey(camelKey)) {
            return request.arguments().get(camelKey);
        }
        return null;
    }

    private static String validateTagItem(Object item, String camelKey) {
        if (item == null) {
            throw new IllegalArgumentException("Tag in '" + camelKey + "' cannot be null");
        }
        String tag = item.toString();
        if (tag.isBlank()) {
            throw new IllegalArgumentException("Tag in '" + camelKey + "' cannot be empty or whitespace");
        }
        if (tag.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Tag in '" + camelKey + "' cannot contain whitespace: '" + tag + "'");
        }
        return tag;
    }
}
