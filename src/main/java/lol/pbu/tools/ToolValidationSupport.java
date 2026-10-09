package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import lol.pbu.z4j.model.EmailCC;
import lol.pbu.z4j.model.EmailCCAllOfAction;
import lol.pbu.z4j.model.Follower;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ToolValidationSupport {

    public static final String PARAM_CUSTOM_STATUS_ID_SNAKE = "custom_status_id";
    public static final String PARAM_TICKET_FORM_ID_SNAKE = "ticket_form_id";
    public static final String PARAM_TICKET_FORM_ID_CAMEL = "ticketFormId";
    public static final String PARAM_ADDITIONAL_TAGS_CAMEL = "additionalTags";
    public static final String PARAM_ADDITIONAL_TAGS_SNAKE = "additional_tags";
    public static final String PARAM_REMOVE_TAGS_CAMEL = "removeTags";
    public static final String PARAM_REMOVE_TAGS_SNAKE = "remove_tags";
    public static final String PARAM_TAGS = "tags";
    public static final String PARAM_REQUESTER_EMAIL = "requesterEmail";
    public static final String PARAM_REQUESTER_EMAIL_SNAKE = "requester_email";
    public static final String PARAM_REQUESTER_ID = "requesterId";
    public static final String PARAM_REQUESTER_ID_SNAKE = "requester_id";
    public static final String PARAM_EMAIL_CCS = "emailCcs";
    public static final String PARAM_EMAIL_CCS_SNAKE = "email_ccs";
    public static final String PARAM_FOLLOWERS = "followers";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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

    public static String validateEmail(@Nullable String email, String paramName) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty() || !EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(paramName + " must be a valid email address, got: '" + email + "'");
        }
        return trimmed;
    }

    @Nullable
    public static String resolveRequesterEmail(@Nullable String explicitEmail, @Nullable CallToolRequest request) {
        if (explicitEmail != null) {
            return validateEmail(explicitEmail, PARAM_REQUESTER_EMAIL);
        }
        if (request != null && request.arguments() != null) {
            if (request.arguments().containsKey(PARAM_REQUESTER_EMAIL)) {
                Object val = request.arguments().get(PARAM_REQUESTER_EMAIL);
                return val != null ? validateEmail(val.toString(), PARAM_REQUESTER_EMAIL) : null;
            }
            if (request.arguments().containsKey(PARAM_REQUESTER_EMAIL_SNAKE)) {
                Object val = request.arguments().get(PARAM_REQUESTER_EMAIL_SNAKE);
                return val != null ? validateEmail(val.toString(), PARAM_REQUESTER_EMAIL_SNAKE) : null;
            }
        }
        return null;
    }

    @Nullable
    public static Long resolveRequesterId(@Nullable Long explicitRequesterId, @Nullable CallToolRequest request) {
        if (explicitRequesterId != null) {
            return explicitRequesterId;
        }
        if (request != null && request.arguments() != null) {
            if (request.arguments().containsKey(PARAM_REQUESTER_ID)) {
                return parseLongArgument(request.arguments().get(PARAM_REQUESTER_ID), PARAM_REQUESTER_ID);
            }
            if (request.arguments().containsKey(PARAM_REQUESTER_ID_SNAKE)) {
                return parseLongArgument(request.arguments().get(PARAM_REQUESTER_ID_SNAKE), PARAM_REQUESTER_ID_SNAKE);
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("java:S1168")
    public static List<EmailCC> resolveAndValidateEmailCcs(
            @Nullable List<?> explicitEmailCcs,
            @Nullable CallToolRequest request
    ) {
        Object raw = extractRawList(explicitEmailCcs, PARAM_EMAIL_CCS, PARAM_EMAIL_CCS_SNAKE, request);
        if (raw == null) {
            return null;
        }
        Collection<?> items = toCollection(raw, PARAM_EMAIL_CCS);
        return items.stream()
                .map(ToolValidationSupport::mapToEmailCc)
                .toList();
    }

    @Nullable
    @SuppressWarnings("java:S1168")
    public static List<Follower> resolveAndValidateFollowers(
            @Nullable List<?> explicitFollowers,
            @Nullable CallToolRequest request
    ) {
        Object raw = extractRawList(explicitFollowers, PARAM_FOLLOWERS, null, request);
        if (raw == null) {
            return null;
        }
        Collection<?> items = toCollection(raw, PARAM_FOLLOWERS);
        return items.stream()
                .map(ToolValidationSupport::mapToFollower)
                .toList();
    }

    private static Object extractRawList(
            @Nullable List<?> explicitList,
            String camelKey,
            @Nullable String snakeKey,
            @Nullable CallToolRequest request
    ) {
        if (explicitList != null) {
            return explicitList;
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

    private static Collection<?> toCollection(Object raw, String paramName) {
        return switch (raw) {
            case Collection<?> coll -> coll;
            case Object[] arr -> Arrays.asList(arr);
            default -> throw new IllegalArgumentException(paramName + " must be a list, got: " + raw.getClass().getSimpleName());
        };
    }

    private static EmailCC mapToEmailCc(Object item) {
        if (item == null) {
            throw new IllegalArgumentException("Entry in 'emailCcs' cannot be null");
        }
        if (item instanceof EmailCC emailCc) {
            if (emailCc.getAction() == null) {
                emailCc.setAction(EmailCCAllOfAction.PUT);
            }
            return emailCc;
        }
        if (item instanceof Number num) {
            long id = num.longValue();
            if (id <= 0) {
                throw new IllegalArgumentException("User ID in 'emailCcs' must be a positive integer, got: " + num);
            }
            return new EmailCC().setUserId(String.valueOf(id)).setAction(EmailCCAllOfAction.PUT);
        }
        if (item instanceof String str) {
            return mapStringToEmailCc(str);
        }
        if (item instanceof Map<?, ?> map) {
            return mapDictToEmailCc(map);
        }
        throw new IllegalArgumentException("Entry in 'emailCcs' must be a user ID (Number), email (String), or Map, got: " + item.getClass().getSimpleName());
    }

    private static EmailCC mapStringToEmailCc(String str) {
        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Entry in 'emailCcs' cannot be empty");
        }
        if (trimmed.contains("@")) {
            String validEmail = validateEmail(trimmed, "user_email in 'emailCcs'");
            return new EmailCC().setUserEmail(validEmail).setAction(EmailCCAllOfAction.PUT);
        }
        try {
            long id = Long.parseLong(trimmed);
            if (id <= 0) {
                throw new IllegalArgumentException("User ID in 'emailCcs' must be a positive integer, got: " + id);
            }
            return new EmailCC().setUserId(String.valueOf(id)).setAction(EmailCCAllOfAction.PUT);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Invalid entry in 'emailCcs': expected a valid email address or numeric user ID, got: '" + str + "'");
        }
    }

    private static EmailCC mapDictToEmailCc(Map<?, ?> map) {
        EmailCCAllOfAction action = parseAction(getMapValue(map, "action"));
        String userId = resolveUserIdFromMap(map, "emailCcs");
        String userEmail = resolveUserEmailFromMap(map, "emailCcs");
        Object nameObj = getMapValue(map, "user_name", "userName", "name");
        String userName = nameObj != null ? nameObj.toString().trim() : null;

        if (userId == null && userEmail == null) {
            throw new IllegalArgumentException("Entry in 'emailCcs' must specify at least 'user_id' or 'user_email'");
        }

        EmailCC emailCc = new EmailCC().setAction(action);
        if (userId != null) {
            emailCc.setUserId(userId);
        }
        if (userEmail != null) {
            emailCc.setUserEmail(userEmail);
        }
        if (userName != null) {
            emailCc.setUserName(userName);
        }
        return emailCc;
    }

    private static Follower mapToFollower(Object item) {
        if (item == null) {
            throw new IllegalArgumentException("Entry in 'followers' cannot be null");
        }
        if (item instanceof Follower follower) {
            if (follower.getAction() == null) {
                follower.setAction(EmailCCAllOfAction.PUT);
            }
            return follower;
        }
        if (item instanceof Number num) {
            long id = num.longValue();
            if (id <= 0) {
                throw new IllegalArgumentException("User ID in 'followers' must be a positive integer, got: " + num);
            }
            return new Follower().setUserId(String.valueOf(id)).setAction(EmailCCAllOfAction.PUT);
        }
        if (item instanceof String str) {
            return mapStringToFollower(str);
        }
        if (item instanceof Map<?, ?> map) {
            return mapDictToFollower(map);
        }
        throw new IllegalArgumentException("Entry in 'followers' must be a user ID (Number), email (String), or Map, got: " + item.getClass().getSimpleName());
    }

    private static Follower mapStringToFollower(String str) {
        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Entry in 'followers' cannot be empty");
        }
        if (trimmed.contains("@")) {
            String validEmail = validateEmail(trimmed, "user_email in 'followers'");
            return new Follower().setUserEmail(validEmail).setAction(EmailCCAllOfAction.PUT);
        }
        try {
            long id = Long.parseLong(trimmed);
            if (id <= 0) {
                throw new IllegalArgumentException("User ID in 'followers' must be a positive integer, got: " + id);
            }
            return new Follower().setUserId(String.valueOf(id)).setAction(EmailCCAllOfAction.PUT);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Invalid entry in 'followers': expected a valid email address or numeric user ID, got: '" + str + "'");
        }
    }

    private static Follower mapDictToFollower(Map<?, ?> map) {
        EmailCCAllOfAction action = parseAction(getMapValue(map, "action"));
        String userId = resolveUserIdFromMap(map, "followers");
        String userEmail = resolveUserEmailFromMap(map, "followers");

        if (userId == null && userEmail == null) {
            throw new IllegalArgumentException("Entry in 'followers' must specify at least 'user_id' or 'user_email'");
        }

        Follower follower = new Follower().setAction(action);
        if (userId != null) {
            follower.setUserId(userId);
        }
        if (userEmail != null) {
            follower.setUserEmail(userEmail);
        }
        return follower;
    }

    private static String resolveUserIdFromMap(Map<?, ?> map, String paramName) {
        Object idObj = getMapValue(map, "user_id", "userId", "id");
        if (idObj == null) {
            return null;
        }
        if (idObj instanceof Number num) {
            if (num.longValue() <= 0) {
                throw new IllegalArgumentException("user_id in '" + paramName + "' must be a positive integer, got: " + num);
            }
            return String.valueOf(num.longValue());
        }
        String idStr = idObj.toString().trim();
        try {
            long id = Long.parseLong(idStr);
            if (id <= 0) {
                throw new IllegalArgumentException("user_id in '" + paramName + "' must be a positive integer, got: " + id);
            }
            return String.valueOf(id);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("user_id in '" + paramName + "' must be a numeric ID, got: '" + idStr + "'");
        }
    }

    private static String resolveUserEmailFromMap(Map<?, ?> map, String paramName) {
        Object emailObj = getMapValue(map, "user_email", "userEmail", "email");
        if (emailObj == null) {
            return null;
        }
        return validateEmail(emailObj.toString().trim(), "user_email in '" + paramName + "'");
    }

    private static Object getMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static EmailCCAllOfAction parseAction(Object actionObj) {
        if (actionObj == null) {
            return EmailCCAllOfAction.PUT;
        }
        if (actionObj instanceof EmailCCAllOfAction act) {
            return act;
        }
        String val = actionObj.toString().trim().toLowerCase();
        try {
            return EmailCCAllOfAction.fromValue(val);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid action '" + actionObj + "', allowed actions are: put, delete", e);
        }
    }
}
