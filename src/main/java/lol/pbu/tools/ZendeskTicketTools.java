package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.model.BatchUpdateResponse;
import lol.pbu.model.BatchUpdateResponse.TicketUpdateResult;
import lol.pbu.model.TicketMutationOptions;
import lol.pbu.model.TicketUpdateInputWithForm;
import lol.pbu.service.ZendeskMetadataService;
import lol.pbu.z4j.client.AttachmentClient;
import lol.pbu.z4j.client.JobStatusClient;
import lol.pbu.z4j.client.TicketClient;
import lol.pbu.z4j.model.AttachmentUploadResponse;
import lol.pbu.z4j.model.JobStatus;
import lol.pbu.z4j.model.JobStatusResponse;
import lol.pbu.z4j.model.Ticket;
import lol.pbu.z4j.model.TicketAuditsResponse;
import lol.pbu.z4j.model.TicketComment;
import lol.pbu.z4j.model.TicketCountResponse;
import lol.pbu.z4j.model.TicketCreateInput;
import lol.pbu.z4j.model.TicketCreateRequest;
import lol.pbu.z4j.model.TicketCustomField;
import lol.pbu.z4j.model.TicketFieldCustomStatusObject;
import lol.pbu.z4j.model.TicketFieldResponse;
import lol.pbu.z4j.model.TicketFieldsResponse;
import lol.pbu.z4j.model.TicketForm;
import lol.pbu.z4j.model.TicketResponse;
import lol.pbu.z4j.model.TicketType;
import lol.pbu.z4j.model.TicketUpdateInput;
import lol.pbu.z4j.model.TicketUpdateInputPriority;
import lol.pbu.z4j.model.TicketUpdateInputStatus;
import lol.pbu.z4j.model.TicketUpdateInputType;
import lol.pbu.z4j.model.TicketUpdateRequest;
import lol.pbu.z4j.model.TicketUpdateResponse;
import lol.pbu.z4j.model.TicketsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings("java:S107")
public class ZendeskTicketTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTicketTools.class);
    private static final String TYPE_INCIDENT = "incident";
    private static final String PARAM_COMMENT = "comment";
    private static final String PARAM_STATUS = "status";
    private static final String PARAM_PRIORITY = "priority";
    private static final String PARAM_IS_PUBLIC = "isPublic";
    private static final String PARAM_UPLOAD_TOKENS = "uploadTokens";
    private static final String PARAM_ATTACHMENT_FILE_PATHS = "attachmentFilePaths";
    private static final String PARAM_PROBLEM_ID = "problemId";
    private static final String PARAM_CONVERT_TO_INCIDENT = "convertToIncident";
    private static final String PARAM_CUSTOM_FIELDS = "customFields";
    private static final String PARAM_REQUESTER_ID = "requesterId";
    private static final String PARAM_TYPE = "type";
    private static final String PARAM_DESCRIPTION = "description";
    private static final String PARAM_CUSTOM_STATUS_ID = "customStatusId";
    private static final String PARAM_CUSTOM_STATUS_ID_SNAKE = "custom_status_id";
    private static final String PARAM_TICKET_FORM_ID = "ticketFormId";
    private static final String PARAM_TICKET_FORM_ID_SNAKE = "ticket_form_id";

    private final TicketClient ticketClient;
    private final AttachmentClient attachmentClient;
    private final JobStatusClient jobStatusClient;
    private final ZendeskMetadataService metadataService;

    @Inject
    public ZendeskTicketTools(
            TicketClient ticketClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ZendeskMetadataService metadataService
    ) {
        this.ticketClient = ticketClient;
        this.attachmentClient = attachmentClient;
        this.jobStatusClient = jobStatusClient;
        this.metadataService = metadataService;
    }

    public ZendeskMetadataService getMetadataService() {
        return metadataService;
    }
    @Tool(description = "Get details of a specific Zendesk ticket by its numeric ID")
    public TicketResponse getTicket(@ToolArg(description = "The numeric ticket ID") Long ticketId) {
        log.info("MCP Tool called: getTicket(id={})", ticketId);
        return ticketClient.showTicket(ticketId).block();
    }

    @Tool(description = "Get details of multiple Zendesk tickets by their numeric IDs")
    public TicketsResponse getTickets(
            @ToolArg(description = "List of numeric ticket IDs to retrieve") List<Long> ticketIds
    ) {
        log.info("MCP Tool called: getTickets(ids={})", ticketIds);
        List<Long> distinctIds = parseDistinctTicketIds(ticketIds);
        if (distinctIds.isEmpty()) {
            return new TicketsResponse(Collections.emptyList());
        }

        List<Ticket> tickets = Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> ticketClient.showTicket(id)
                        .map(TicketResponse::getTicket)
                        .onErrorMap(e -> new RuntimeException(String.format("Failed to fetch ticket %d: [%s] %s", id, e.getClass().getSimpleName(), e.getMessage())))
                        .switchIfEmpty(Mono.error(new RuntimeException(String.format("Failed to fetch ticket %d: [EmptyResult] Ticket not found", id)))), 10)
                .collectList()
                .block();

        return new TicketsResponse(tickets != null ? tickets : Collections.emptyList());
    }

    private List<Long> parseDistinctTicketIds(@Nullable List<?> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinctIds = new ArrayList<>();
        for (Object obj : rawIds) {
            if (obj instanceof Number num) {
                distinctIds.add(num.longValue());
            } else if (obj != null) {
                try {
                    distinctIds.add(Long.parseLong(obj.toString().trim()));
                } catch (NumberFormatException _) {
                    log.warn("Invalid ticket ID format: {}", obj);
                }
            }
        }
        return distinctIds.stream().distinct().toList();
    }

    @Tool(description = "List recent Zendesk tickets")
    public TicketsResponse listTickets() {
        log.info("MCP Tool called: listTickets()");
        return ticketClient.listTickets(null).block();
    }

    @Tool(description = "Get ticket count information from Zendesk")
    public TicketCountResponse getTicketCount() {
        log.info("MCP Tool called: getTicketCount()");
        return ticketClient.getTicketCount().block();
    }
    List<String> resolveUploadTokens(List<String> uploadTokens, List<String> attachmentFilePaths) {
        List<String> tokens = new ArrayList<>();
        if (uploadTokens != null) {
            tokens.addAll(uploadTokens);
        }
        if (attachmentFilePaths != null) {
            for (String filePath : attachmentFilePaths) {
                if (StringUtils.isNotEmpty(filePath)) {
                    AttachmentUploadResponse uploadResp = uploadAttachment(filePath, null);
                    if (uploadResp != null && uploadResp.getUpload() != null && uploadResp.getUpload().getToken() != null) {
                        tokens.add(uploadResp.getUpload().getToken());
                    }
                }
            }
        }
        return tokens;
    }

    @Tool(description = "Create a new Zendesk ticket")
    public TicketResponse createTicket(
            @ToolArg(description = "The subject of the ticket") String subject,
            @ToolArg(description = "The initial comment / description of the ticket") @Nullable String comment,
            @ToolArg(description = "Required. Whether the initial comment is public (true) or private internal note (false)") Boolean isPublic,
            @ToolArg(description = "Priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths,
            @ToolArg(description = "Optional custom fields as a list of objects containing 'id' and 'value', e.g. [{'id': 1234, 'value': 'foo'}]") @Nullable List<Map<String, Object>> customFields,
            @ToolArg(description = "Optional requester ID (Zendesk user ID on whose behalf the ticket is created / reassigned)") @Nullable Long requesterId,
            @ToolArg(description = "Optional ticket type: 'problem', 'incident', 'question', 'task', or empty/none to unset") @Nullable String type,
            @ToolArg(description = "Optional numeric ID of a custom ticket status (from listCustomStatuses)") @Nullable Long customStatusId,
            @ToolArg(description = "Optional numeric ticket form ID to specify the form used for this ticket") @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        log.info("MCP Tool called: createTicket(subject='{}')", subject);
        validateKnownParameters(request, "createTicket", "subject", PARAM_COMMENT, PARAM_IS_PUBLIC, PARAM_PRIORITY, PARAM_STATUS, PARAM_UPLOAD_TOKENS, PARAM_ATTACHMENT_FILE_PATHS, PARAM_CUSTOM_FIELDS, PARAM_REQUESTER_ID, PARAM_TYPE, PARAM_DESCRIPTION, PARAM_CUSTOM_STATUS_ID, PARAM_CUSTOM_STATUS_ID_SNAKE, PARAM_TICKET_FORM_ID, PARAM_TICKET_FORM_ID_SNAKE);

        String initialComment = resolveInitialComment(comment, request);
        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);
        validateCreateTicketInput(subject, initialComment, isPublic, tokens);

        TicketComment ticketComment = new TicketComment().setBody(initialComment);
        ticketComment.setIsPublic(isPublic);
        if (!tokens.isEmpty()) {
            ticketComment.setUploads(tokens);
        }
        TicketCreateInput input = new TicketCreateInput(ticketComment);
        input.setSubject(subject);
        input.setRawSubject(subject);

        applyPriorityAndStatus(input, priority, status);
        List<TicketCustomField> parsedCustomFields = parseCustomFields(customFields);
        if (!parsedCustomFields.isEmpty()) {
            input.setCustomFields(parsedCustomFields);
        }
        applyRequesterAndType(input, requesterId, type);

        final Long resolvedTicketFormId = resolveTicketFormId(ticketFormId, request);
        if (resolvedTicketFormId != null) {
            validateTicketFormBounds(resolvedTicketFormId);
            validateTicketForm(resolvedTicketFormId);
            input.setTicketFormId(resolvedTicketFormId);
        }

        final Long resolvedCustomStatusId = resolveCustomStatusId(customStatusId, request);
        if (resolvedCustomStatusId != null) {
            validateAndApplyCustomStatus(input, resolvedCustomStatusId, resolvedTicketFormId, status);
        }

        return ticketClient.createTicket(new TicketCreateRequest(input)).block();
    }

    private String resolveInitialComment(@Nullable String comment, @Nullable CallToolRequest request) {
        String initialComment = comment;
        if (request != null && request.arguments() != null && request.arguments().containsKey(PARAM_DESCRIPTION)) {
            Object descObj = request.arguments().get(PARAM_DESCRIPTION);
            String desc = descObj != null ? descObj.toString() : null;
            if (desc != null && !desc.isBlank()) {
                if (initialComment != null && !initialComment.isBlank() && !initialComment.equals(desc)) {
                    throw new IllegalArgumentException("Provide either 'comment' or 'description' for the initial ticket message, not both with different content.");
                }
                if (initialComment == null || initialComment.isBlank()) {
                    initialComment = desc;
                }
            }
        }
        return initialComment;
    }

    private void validateCreateTicketInput(String subject, String initialComment, Boolean isPublic, List<String> tokens) {
        if ((initialComment == null || initialComment.isBlank()) && tokens.isEmpty()) {
            throw new IllegalArgumentException("Either 'comment' or 'description' is required when creating a ticket.");
        }
        if (isPublic == null) {
            throw new IllegalArgumentException("isPublic is required when creating a ticket. Set to true for a public initial comment, or false for an internal note.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Ticket 'subject' is required and cannot be empty.");
        }
    }

    private void applyPriorityAndStatus(TicketCreateInput input, @Nullable String priority, @Nullable String status) {
        if (priority != null && !priority.isBlank()) {
            try {
                input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown priority '{}', ignoring", priority);
            }
        }
        if (status != null && !status.isBlank()) {
            try {
                input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown status '{}', ignoring", status);
            }
        }
    }

    private void applyRequesterAndType(TicketCreateInput input, @Nullable Long requesterId, @Nullable String type) {
        if (requesterId != null) {
            if (requesterId > Integer.MAX_VALUE || requesterId < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("requesterId " + requesterId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits requester_id on ticket inputs to 32-bit integers.");
            }
            input.setRequesterId(requesterId.intValue());
        }
        if (type != null && !type.isBlank()) {
            if (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty()) {
                input.setType(null);
            } else {
                TicketUpdateInputType parsedType = parseTicketType(type);
                input.setType(parsedType);
            }
        }
    }

    private void validateAndApplyCustomStatus(TicketCreateInput input, @Nullable Long resolvedCustomStatusId, @Nullable Long resolvedTicketFormId, @Nullable String status) {
        if (resolvedCustomStatusId == null) {
            return;
        }
        validateCustomStatusBounds(resolvedCustomStatusId);
        String targetStatus = (status != null && !status.isBlank()) ? status : "new";
        TicketFieldCustomStatusObject validated = validateCustomStatus(resolvedCustomStatusId, targetStatus);
        if (resolvedTicketFormId != null && validated != null) {
            validateCustomStatusForForm(validated, resolvedTicketFormId);
        }
        input.setCustomStatusId(resolvedCustomStatusId.intValue());
    }

    public TicketResponse createTicket(
            String subject,
            @Nullable String comment,
            Boolean isPublic,
            @Nullable String priority,
            @Nullable String status,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            CallToolRequest request
    ) {
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, customStatusId, null, request);
    }


    public TicketResponse createTicket(
            String subject,
            String comment,
            Boolean isPublic,
            String priority,
            String status,
            List<String> uploadTokens,
            List<String> attachmentFilePaths
    ) {
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, null, null, null, null, (CallToolRequest) null);
    }

    public TicketResponse createTicket(
            String subject,
            String comment,
            Boolean isPublic,
            String priority,
            String status,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type
    ) {
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, null, (CallToolRequest) null);
    }

    public TicketResponse createTicket(
            String subject,
            String comment,
            Boolean isPublic,
            String priority,
            String status,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            CallToolRequest request
    ) {
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, null, request);
    }

    private TicketUpdateInputType parseTicketType(String type) {
        if (type != null) {
            String normalized = type.trim().toLowerCase();
            if ("problem".equals(normalized) || TYPE_INCIDENT.equals(normalized) || "question".equals(normalized) || "task".equals(normalized)) {
                return TicketUpdateInputType.fromValue(normalized);
            }
        }
        throw new IllegalArgumentException("Invalid ticket type: '" + type + "'. Allowed types are: problem, incident, question, task.");
    }

    private void validateTicketTypeChange(Ticket currentTicket, TicketUpdateInputType newType, boolean isTypeUnset) {
        if (currentTicket == null) return;
        if (currentTicket.getType() == TicketType.PROBLEM && Boolean.TRUE.equals(currentTicket.getHasIncidents()) && (isTypeUnset || newType != TicketUpdateInputType.PROBLEM)) {
            throw new IllegalArgumentException("ticket #" + currentTicket.getId() + " is a parent problem with linked incidents — reassign or resolve those first");
        }
    }

    List<TicketCustomField> parseCustomFields(List<Map<String, Object>> customFields) {
        if (customFields == null || customFields.isEmpty()) return Collections.emptyList();
        List<TicketCustomField> result = new ArrayList<>();
        for (Map<String, Object> cf : customFields) {
            if (cf == null) continue;
            Object idObj = cf.get("id");
            Object value = cf.get("value");
            if (idObj == null) throw new IllegalArgumentException("Custom field must have an 'id'");
            Long id;
            if (idObj instanceof Number number) {
                id = number.longValue();
            } else {
                try {
                    id = Long.parseLong(idObj.toString().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Custom field 'id' must be a numeric ID, got: '" + idObj + "'", e);
                }
            }
            result.add(new TicketCustomField.Raw(id, value));
        }
        return result;
    }

    private void applyComment(TicketUpdateInput input, @Nullable String comment, @Nullable Boolean isPublic, List<String> tokens) {
        if (StringUtils.isNotEmpty(comment) || !tokens.isEmpty()) {
            if (isPublic == null) {
                throw new IllegalArgumentException("isPublic is required when a comment or attachment is provided. Set to true for a public reply, or false for an internal note.");
            }
            TicketComment ticketComment = new TicketComment();
            if (StringUtils.isNotEmpty(comment)) {
                ticketComment.setBody(comment);
            }
            ticketComment.setIsPublic(isPublic);
            if (!tokens.isEmpty()) {
                ticketComment.setUploads(tokens);
            }
            input.setComment(ticketComment);
        }
    }

    private void applyStatusAndPriority(TicketUpdateInput input, @Nullable String status, @Nullable String priority) {
        if (status != null && !status.isBlank()) {
            try {
                input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown status '{}', ignoring", status);
            }
        }
        if (priority != null && !priority.isBlank()) {
            try {
                input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown priority '{}', ignoring", priority);
            }
        }
    }

    private TicketUpdateInput populateInputFromParams(TicketUpdateInput input, String comment, String status, String priority, Boolean isPublic, List<String> tokens, List<TicketCustomField> customFields) {
        applyComment(input, comment, isPublic, tokens);
        applyStatusAndPriority(input, status, priority);
        if (customFields != null && !customFields.isEmpty()) {
            input.setCustomFields(customFields);
        }
        return input;
    }

    TicketUpdateInput buildInputFromParams(String comment, String status, String priority, Boolean isPublic, List<String> tokens, List<TicketCustomField> customFields) {
        return populateInputFromParams(new TicketUpdateInput(), comment, status, priority, isPublic, tokens, customFields);
    }

    TicketUpdateInput buildTicketUpdateInput(
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> tokens,
            List<TicketCustomField> customFields,
            Long requesterId,
            String type,
            Boolean convertToIncident,
            Long customStatusId,
            Long ticketFormId
    ) {
        TicketUpdateInput input;
        if (ticketFormId != null) {
            TicketUpdateInputWithForm formInput = new TicketUpdateInputWithForm();
            formInput.setTicketFormId(ticketFormId);
            input = formInput;
        } else {
            input = new TicketUpdateInput();
        }
        populateInputFromParams(input, comment, status, priority, isPublic, tokens, customFields);

        if (requesterId != null) {
            if (requesterId > Integer.MAX_VALUE || requesterId < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("requesterId " + requesterId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits requester_id on ticket inputs to 32-bit integers.");
            }
            input.setRequesterId(requesterId.intValue());
        }

        if (type != null) {
            if (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty()) {
                input.setType(null);
            } else {
                input.setType(parseTicketType(type));
            }
        } else if (Boolean.TRUE.equals(convertToIncident)) {
            input.setType(TicketUpdateInputType.INCIDENT);
        }

        if (customStatusId != null) {
            input.setCustomStatusId(customStatusId.intValue());
        }
        return input;
    }

    TicketUpdateInput buildTicketUpdateInput(
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> tokens,
            List<TicketCustomField> customFields,
            Long requesterId,
            String type,
            Boolean convertToIncident,
            Long customStatusId
    ) {
        return buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident, customStatusId, null);
    }

    TicketUpdateInput buildTicketUpdateInput(
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> tokens,
            List<TicketCustomField> customFields,
            Long requesterId,
            String type,
            Boolean convertToIncident
    ) {
        return buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident, null, null);
    }

    private void validateProblemTypeConflict(Long problemId, String type) {
        if (problemId != null && type != null && !TYPE_INCIDENT.equalsIgnoreCase(type.trim())) {
            throw new IllegalArgumentException("Cannot specify type '" + type + "' when linking to a problem ticket. Linked tickets must be of type 'incident'.");
        }
    }

    private static boolean isTypeUnset(String type) {
        return type != null && (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty());
    }

    private void validateProblemTarget(Long problemId) {
        if (problemId == null) return;
        if (problemId <= 0) {
            throw new IllegalArgumentException("problemId must be a positive integer, got: " + problemId);
        }
        if (problemId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("problemId " + problemId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits problem_id on ticket inputs to 32-bit integers.");
        }
        try {
            TicketResponse resp = ticketClient.showTicket(problemId).block();
            Ticket problemTicket = resp != null ? resp.getTicket() : null;
            if (problemTicket == null) {
                throw new IllegalArgumentException("Target problem ticket #" + problemId + " could not be retrieved. Does it exist?");
            }
            if (problemTicket.getType() != TicketType.PROBLEM) {
                throw new IllegalArgumentException("Target ticket #" + problemId + " is not of type 'problem'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception _) {
            throw new IllegalArgumentException("Target problem ticket #" + problemId + " could not be retrieved. Does it exist?");
        }
    }

    private void applyProblemIdLogic(Ticket currentTicket, TicketUpdateInput input, Long problemId, Boolean convertToIncident) {
        if (problemId == null) return;
        TicketType currentType = currentTicket.getType();
        boolean isProblemParent = currentType == TicketType.PROBLEM && Boolean.TRUE.equals(currentTicket.getHasIncidents());
        
        if (isProblemParent) {
            throw new IllegalArgumentException("ticket #" + currentTicket.getId() + " is a parent problem with linked incidents — reassign or resolve those first");
        }
        
        if (currentType != TicketType.INCIDENT && !Boolean.TRUE.equals(convertToIncident)) {
            throw new IllegalArgumentException("Ticket #" + currentTicket.getId() + " is not an incident. Pass convertToIncident=true to explicitly convert it.");
        }
        input.setType(TicketUpdateInputType.INCIDENT);
        input.setProblemId(problemId.intValue());
    }

    @Tool(description = "Update an existing Zendesk ticket with a comment, status, priority, attachments, custom status, ticket form, or link to a problem ticket")
    public TicketUpdateResponse updateTicket(
            @ToolArg(description = "The numeric ticket ID to update") Long ticketId,
            @ToolArg(description = "Comment text to add to the ticket") @Nullable String comment,
            @ToolArg(description = "New status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "New priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Required if a comment or attachment is provided. Whether the comment is public (true) or private internal note (false)") @Nullable Boolean isPublic,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths,
            @ToolArg(description = "Optional ID of the parent problem ticket to link this incident to") @Nullable Long problemId,
            @ToolArg(description = "Required if setting problemId on a ticket that is not currently an incident. Set to true to explicitly convert it.") @Nullable Boolean convertToIncident,
            @ToolArg(description = "Optional custom fields as a list of objects containing 'id' and 'value', e.g. [{'id': 1234, 'value': 'foo'}]") @Nullable List<Map<String, Object>> customFields,
            @ToolArg(description = "Optional requester ID (Zendesk user ID on whose behalf the ticket is created / reassigned)") @Nullable Long requesterId,
            @ToolArg(description = "Optional ticket type: 'problem', 'incident', 'question', 'task', or empty/none to unset") @Nullable String type,
            @ToolArg(description = "Optional numeric ID of a custom ticket status (from listCustomStatuses)") @Nullable Long customStatusId,
            @ToolArg(description = "Optional numeric ticket form ID to change the form used for this ticket") @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        TicketMutationOptions options = TicketMutationOptions.builder()
                .comment(comment)
                .status(status)
                .priority(priority)
                .isPublic(isPublic)
                .uploadTokens(uploadTokens)
                .attachmentFilePaths(attachmentFilePaths)
                .problemId(problemId)
                .convertToIncident(convertToIncident)
                .customFields(customFields)
                .requesterId(requesterId)
                .type(type)
                .customStatusId(customStatusId)
                .ticketFormId(ticketFormId)
                .build();
        return updateTicket(ticketId, options, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            TicketMutationOptions options,
            @Nullable CallToolRequest request
    ) {
        if (options == null) {
            options = TicketMutationOptions.builder().build();
        }
        log.info("MCP Tool called: updateTicket(id={})", ticketId);
        List<String> tokens = resolveUploadTokens(options.uploadTokens(), options.attachmentFilePaths());
        validateKnownParameters(request, "updateTicket", "ticketId", PARAM_COMMENT, PARAM_STATUS, PARAM_PRIORITY, PARAM_IS_PUBLIC, PARAM_UPLOAD_TOKENS, PARAM_ATTACHMENT_FILE_PATHS, PARAM_PROBLEM_ID, PARAM_CONVERT_TO_INCIDENT, PARAM_CUSTOM_FIELDS, PARAM_REQUESTER_ID, PARAM_TYPE, PARAM_CUSTOM_STATUS_ID, PARAM_CUSTOM_STATUS_ID_SNAKE, PARAM_TICKET_FORM_ID, PARAM_TICKET_FORM_ID_SNAKE);
        validateProblemTypeConflict(options.problemId(), options.type());
        validateProblemTarget(options.problemId());

        final Long resolvedTicketFormId = resolveAndValidateTicketFormId(options.ticketFormId(), request);
        final Long resolvedCustomStatusId = resolveCustomStatusId(options.customStatusId(), request);
        TicketFieldCustomStatusObject validatedCustomStatus = validateCustomStatusForMutation(resolvedCustomStatusId, resolvedTicketFormId, options.status());

        List<TicketCustomField> parsedCustomFields = parseCustomFields(options.customFields());
        TicketUpdateInput input = buildTicketUpdateInput(options.comment(), options.status(), options.priority(), options.isPublic(), tokens, parsedCustomFields, options.requesterId(), options.type(), options.convertToIncident(), resolvedCustomStatusId, resolvedTicketFormId);

        final boolean isTypeUnset = isTypeUnset(options.type());
        final TicketUpdateInputType parsedType = (options.type() != null && !isTypeUnset) ? parseTicketType(options.type()) : null;

        if (needsCurrentTicket(options, resolvedCustomStatusId, isTypeUnset, parsedType)) {
            validateAndApplyCurrentTicket(ticketId, options, input, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus);
        }

        return ticketClient.updateTicket(ticketId, new TicketUpdateRequest(input)).block();
    }

    private Long resolveAndValidateTicketFormId(@Nullable Long ticketFormId, @Nullable CallToolRequest request) {
        Long resolvedTicketFormId = resolveTicketFormId(ticketFormId, request);
        if (resolvedTicketFormId != null) {
            validateTicketFormBounds(resolvedTicketFormId);
            validateTicketForm(resolvedTicketFormId);
        }
        return resolvedTicketFormId;
    }

    private TicketFieldCustomStatusObject validateCustomStatusForMutation(@Nullable Long resolvedCustomStatusId, @Nullable Long resolvedTicketFormId, @Nullable String status) {
        if (resolvedCustomStatusId == null) {
            return null;
        }
        validateCustomStatusBounds(resolvedCustomStatusId);
        TicketFieldCustomStatusObject validatedCustomStatus = validateCustomStatus(resolvedCustomStatusId, status);
        if (resolvedTicketFormId != null) {
            validateCustomStatusForForm(validatedCustomStatus, resolvedTicketFormId);
        }
        return validatedCustomStatus;
    }

    private boolean needsCurrentTicket(TicketMutationOptions options, @Nullable Long resolvedCustomStatusId, boolean isTypeUnset, @Nullable TicketUpdateInputType parsedType) {
        return options.problemId() != null
                || (options.type() != null && (isTypeUnset || parsedType != TicketUpdateInputType.PROBLEM))
                || (resolvedCustomStatusId != null && StringUtils.isEmpty(options.status()));
    }

    private void validateAndApplyCurrentTicket(
            Long ticketId,
            TicketMutationOptions options,
            TicketUpdateInput input,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus
    ) {
        Mono<TicketResponse> showMono = ticketClient.showTicket(ticketId);
        TicketResponse showResp = showMono != null ? showMono.block() : null;
        if (showResp == null || showResp.getTicket() == null) {
            throw new IllegalArgumentException("Ticket #" + ticketId + " could not be retrieved. Does it exist?");
        }
        Ticket currentTicket = showResp.getTicket();
        validateTicketState(ticketId, currentTicket, options, input, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus);
    }

    private void validateTicketState(
            Long ticketId,
            Ticket currentTicket,
            TicketMutationOptions options,
            TicketUpdateInput input,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus
    ) {
        if (options.type() != null) {
            validateTicketTypeChange(currentTicket, parsedType, isTypeUnset);
        }
        if (options.problemId() != null) {
            applyProblemIdLogic(currentTicket, input, options.problemId(), options.convertToIncident());
        }
        if (resolvedCustomStatusId != null && StringUtils.isEmpty(options.status())) {
            validateTicketCustomStatusCategoryMatch(ticketId, currentTicket, validatedCustomStatus, resolvedCustomStatusId);
        }
        Long formIdToValidate = resolvedTicketFormId != null ? resolvedTicketFormId : currentTicket.getTicketFormId();
        if (validatedCustomStatus != null && formIdToValidate != null) {
            validateCustomStatusForForm(validatedCustomStatus, formIdToValidate);
        }
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            TicketMutationOptions options
    ) {
        return updateTicket(ticketId, options, null);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            Long customStatusId,
            CallToolRequest request
    ) {
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, null, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            CallToolRequest request
    ) {
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, null, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            CallToolRequest request
    ) {
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, null, null, null, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields
    ) {
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, null, null, null, (CallToolRequest) null);
    }
    public static final long MAX_ATTACHMENT_SIZE_BYTES = 50L * 1024 * 1024; // 50MB

    static final Map<String, String> EXTENSION_MIME_TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("jsonl", "application/json"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("md", "text/markdown"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("svg", "image/svg+xml")
    );

    Path validateAndResolveFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }
        String expanded = filePath.trim();
        if (expanded.equals("~")) {
            expanded = System.getProperty("user.home");
        } else if (expanded.startsWith("~" + File.separator) || expanded.startsWith("~/")) {
            expanded = System.getProperty("user.home") + expanded.substring(1);
        }
        Path path = Path.of(expanded).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found at path: " + filePath);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path is a directory, not a regular file: " + filePath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable (check permissions): " + filePath);
        }
        try {
            long size = Files.size(path);
            if (size == 0) {
                throw new IllegalArgumentException("Cannot upload empty file (0 bytes): " + filePath);
            }
            if (size > MAX_ATTACHMENT_SIZE_BYTES) {
                throw new IllegalArgumentException("File size exceeds maximum upload limit of 50MB (" + size + " bytes): " + filePath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to check file size for " + filePath + ": " + e.getMessage(), e);
        }
        return path;
    }

    String resolveTargetFilename(Path path, @Nullable String filename) {
        if (filename != null && !filename.isBlank()) {
            String trimmed = filename.trim();
            Path fnPath = Path.of(trimmed).getFileName();
            return fnPath != null ? fnPath.toString() : trimmed;
        }
        Path fileNamePath = path.getFileName();
        return fileNamePath != null ? fileNamePath.toString() : "attachment.bin";
    }

    void validateFilenameExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            throw new IllegalArgumentException(
                    "Filename must include a valid file extension (e.g. .png, .txt, .pdf): " + filename);
        }
    }

    String probeContentType(Path path, String targetFilename) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (IOException _) {
            // Fall back to framework and extension-based lookup
        }
        int lastDot = targetFilename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < targetFilename.length() - 1) {
            String ext = targetFilename.substring(lastDot + 1).toLowerCase();
            Optional<MediaType> mediaType = MediaType.forExtension(ext);
            if (mediaType.isPresent()) {
                return mediaType.get().getName();
            }
            String mime = EXTENSION_MIME_TYPES.get(ext);
            if (mime != null) {
                return mime;
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @Tool(description = "Upload a file from the local file system to Zendesk to obtain an upload token for use in createTicket, updateTicket, or batchUpdateTickets")
    public AttachmentUploadResponse uploadAttachment(
            @ToolArg(description = "The absolute path to the local file to upload") String filePath,
            @ToolArg(description = "Optional filename to use for the attachment. If not provided, the local filename is used.") @Nullable String filename,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: uploadAttachment(filePath='{}', filename='{}')", filePath, filename);
        validateKnownParameters(request, "uploadAttachment", "filePath", "filename");
        Path path = validateAndResolveFilePath(filePath);
        String targetFilename = resolveTargetFilename(path, filename);
        validateFilenameExtension(targetFilename);

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Failed to read file from {}: {}", filePath, e.getMessage(), e);
            throw new IllegalArgumentException("Failed to read file at " + filePath + ": " + e.getMessage(), e);
        }

        String contentType = probeContentType(path, targetFilename);
        log.debug("Uploading attachment: filename='{}', contentType='{}', size={} bytes", targetFilename, contentType, bytes.length);

        return attachmentClient.uploadAttachment(targetFilename, contentType, bytes).block();
    }

    public AttachmentUploadResponse uploadAttachment(String filePath, @Nullable String filename) {
        return uploadAttachment(filePath, filename, null);
    }


    @Tool(description = "Batch update multiple Zendesk tickets by their numeric IDs with a comment, status, priority, attachments, custom status, ticket form, or link to a problem ticket. Supports concurrent immediate updates or Zendesk async bulk jobs.")
    public BatchUpdateResponse batchUpdateTickets(
            @ToolArg(description = "List of numeric ticket IDs to update") List<Long> ticketIds,
            @ToolArg(description = "Comment text to add to the tickets") @Nullable String comment,
            @ToolArg(description = "New status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "New priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Required if a comment or attachment is provided. Whether the comment is public (true) or private internal note (false)") @Nullable Boolean isPublic,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths,
            @ToolArg(description = "If true, queues an async bulk job in Zendesk (PUT /api/v2/tickets/update_many) returning JobStatus. If false (default), updates tickets concurrently via Reactor returning immediate per-ticket results.") @Nullable Boolean asyncBulk,
            @ToolArg(description = "Optional ID of the parent problem ticket to link these incidents to") @Nullable Long problemId,
            @ToolArg(description = "Required if setting problemId on tickets that are not currently incidents. Set to true to explicitly convert them.") @Nullable Boolean convertToIncident,
            @ToolArg(description = "Optional custom fields as a list of objects containing 'id' and 'value', e.g. [{'id': 1234, 'value': 'foo'}]") @Nullable List<Map<String, Object>> customFields,
            @ToolArg(description = "Optional requester ID (Zendesk user ID on whose behalf the ticket is created / reassigned)") @Nullable Long requesterId,
            @ToolArg(description = "Optional ticket type: 'problem', 'incident', 'question', 'task', or empty/none to unset") @Nullable String type,
            @ToolArg(description = "Optional numeric ID of a custom ticket status (from listCustomStatuses)") @Nullable Long customStatusId,
            @ToolArg(description = "Optional numeric ticket form ID to change the form used for these tickets") @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        TicketMutationOptions options = TicketMutationOptions.builder()
                .comment(comment)
                .status(status)
                .priority(priority)
                .isPublic(isPublic)
                .uploadTokens(uploadTokens)
                .attachmentFilePaths(attachmentFilePaths)
                .problemId(problemId)
                .convertToIncident(convertToIncident)
                .customFields(customFields)
                .requesterId(requesterId)
                .type(type)
                .customStatusId(customStatusId)
                .ticketFormId(ticketFormId)
                .build();
        return batchUpdateTickets(ticketIds, options, asyncBulk, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options,
            @Nullable Boolean asyncBulk,
            @Nullable CallToolRequest request
    ) {
        if (options == null) {
            options = TicketMutationOptions.builder().build();
        }
        log.info("MCP Tool called: batchUpdateTickets(ids={}, asyncBulk={})", ticketIds, asyncBulk);
        validateKnownParameters(request, "batchUpdateTickets", "ticketIds", PARAM_COMMENT, PARAM_STATUS, PARAM_PRIORITY, PARAM_IS_PUBLIC, PARAM_UPLOAD_TOKENS, PARAM_ATTACHMENT_FILE_PATHS, "asyncBulk", PARAM_PROBLEM_ID, PARAM_CONVERT_TO_INCIDENT, PARAM_CUSTOM_FIELDS, PARAM_REQUESTER_ID, PARAM_TYPE, PARAM_CUSTOM_STATUS_ID, PARAM_CUSTOM_STATUS_ID_SNAKE, PARAM_TICKET_FORM_ID, PARAM_TICKET_FORM_ID_SNAKE);
        List<Long> distinctIds = parseDistinctTicketIds(ticketIds);
        if (distinctIds.isEmpty()) {
            return new BatchUpdateResponse(null, null, Collections.emptyList());
        }

        List<String> tokens = resolveUploadTokens(options.uploadTokens(), options.attachmentFilePaths());
        validateProblemTypeConflict(options.problemId(), options.type());
        validateProblemTarget(options.problemId());

        final Long resolvedTicketFormId = resolveAndValidateTicketFormId(options.ticketFormId(), request);
        final Long resolvedCustomStatusId = resolveCustomStatusId(options.customStatusId(), request);
        TicketFieldCustomStatusObject validatedCustomStatus = validateCustomStatusForMutation(resolvedCustomStatusId, resolvedTicketFormId, options.status());

        final boolean isTypeUnset = isTypeUnset(options.type());
        final TicketUpdateInputType parsedType = (options.type() != null && !isTypeUnset) ? parseTicketType(options.type()) : null;
        List<TicketCustomField> parsedCustomFields = parseCustomFields(options.customFields());

        if (Boolean.TRUE.equals(asyncBulk)) {
            return executeAsyncBulkBatchUpdate(distinctIds, options, tokens, parsedCustomFields, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus, parsedType, isTypeUnset);
        }

        return executeConcurrentBatchUpdate(distinctIds, options, tokens, parsedCustomFields, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus, parsedType, isTypeUnset);
    }

    private BatchUpdateResponse executeAsyncBulkBatchUpdate(
            List<Long> distinctIds,
            TicketMutationOptions options,
            List<String> tokens,
            List<TicketCustomField> parsedCustomFields,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset
    ) {
        validateCurrentTicketsForBulkAsync(distinctIds, options, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus, parsedType, isTypeUnset);

        TicketUpdateInput input = buildTicketUpdateInput(options.comment(), options.status(), options.priority(), options.isPublic(), tokens, parsedCustomFields, options.requesterId(), options.type(), options.convertToIncident(), resolvedCustomStatusId, resolvedTicketFormId);
        if (options.problemId() != null) {
            input.setProblemId(options.problemId().intValue());
            input.setType(TicketUpdateInputType.INCIDENT);
        }

        List<JobStatus> jobStatuses = sendBulkUpdateChunks(distinctIds, input);
        return new BatchUpdateResponse(jobStatuses.size() == 1 ? jobStatuses.get(0) : null, jobStatuses.isEmpty() ? null : jobStatuses, null);
    }

    private void validateCurrentTicketsForBulkAsync(
            List<Long> distinctIds,
            TicketMutationOptions options,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset
    ) {
        if (!needsCurrentTicket(options, resolvedCustomStatusId, isTypeUnset, parsedType)) {
            return;
        }
        List<Ticket> currentTickets = Flux.fromIterable(distinctIds)
                .flatMap(id -> {
                    Mono<TicketResponse> showMono = ticketClient.showTicket(id);
                    if (showMono == null) {
                        return Mono.empty();
                    }
                    return showMono.onErrorResume(e -> Mono.empty())
                            .filter(resp -> resp != null && resp.getTicket() != null)
                            .map(TicketResponse::getTicket);
                })
                .collectList()
                .block();
        if (currentTickets != null) {
            for (Ticket currentTicket : currentTickets) {
                TicketUpdateInput testInput = new TicketUpdateInput();
                validateTicketState(currentTicket.getId(), currentTicket, options, testInput, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus);
            }
        }
    }

    private List<JobStatus> sendBulkUpdateChunks(List<Long> distinctIds, TicketUpdateInput input) {
        List<JobStatus> jobStatuses = new ArrayList<>();
        for (int i = 0; i < distinctIds.size(); i += 100) {
            List<Long> chunk = distinctIds.subList(i, Math.min(distinctIds.size(), i + 100));
            String idsStr = chunk.stream().map(Object::toString).collect(Collectors.joining(","));
            JobStatusResponse jobResponse = ticketClient.updateManyTickets(idsStr, new TicketUpdateRequest(input)).block();
            if (jobResponse != null && jobResponse.getJobStatus() != null) {
                jobStatuses.add(jobResponse.getJobStatus());
            }
        }
        return jobStatuses;
    }

    private BatchUpdateResponse executeConcurrentBatchUpdate(
            List<Long> distinctIds,
            TicketMutationOptions options,
            List<String> tokens,
            List<TicketCustomField> parsedCustomFields,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset
    ) {
        List<TicketUpdateResult> results = Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> {
                    TicketUpdateInput input = buildTicketUpdateInput(options.comment(), options.status(), options.priority(), options.isPublic(), tokens, parsedCustomFields, options.requesterId(), options.type(), options.convertToIncident(), resolvedCustomStatusId, resolvedTicketFormId);
                    return resolveTicketUpdateInput(id, options, input, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus)
                            .flatMap(resolvedInput -> ticketClient.updateTicket(id, new TicketUpdateRequest(resolvedInput)))
                            .map(resp -> new TicketUpdateResult(id, true, resp != null ? resp.getTicket() : null, null))
                            .onErrorResume(e -> {
                                log.warn("Failed to update ticket {}: {}", id, e.getMessage());
                                return Mono.just(new TicketUpdateResult(id, false, null, e.getMessage()));
                            });
                }, 10)
                .collectList()
                .block();

        return new BatchUpdateResponse(null, null, results != null ? results : Collections.emptyList());
    }

    private Mono<TicketUpdateInput> resolveTicketUpdateInput(
            Long id,
            TicketMutationOptions options,
            TicketUpdateInput input,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus
    ) {
        if (!needsCurrentTicket(options, resolvedCustomStatusId, isTypeUnset, parsedType)) {
            return Mono.just(input);
        }
        Mono<TicketResponse> showMono = ticketClient.showTicket(id);
        if (showMono == null) {
            return Mono.just(input);
        }
        return showMono
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket #" + id + " could not be retrieved. Does it exist?")))
                .flatMap(resp -> {
                    if (resp.getTicket() == null) {
                        return Mono.error(new IllegalArgumentException("Ticket #" + id + " could not be retrieved. Does it exist?"));
                    }
                    Ticket currentTicket = resp.getTicket();
                    validateTicketState(id, currentTicket, options, input, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus);
                    return Mono.just(input);
                });
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options
    ) {
        return batchUpdateTickets(ticketIds, options, null, null);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options,
            @Nullable Boolean asyncBulk
    ) {
        return batchUpdateTickets(ticketIds, options, asyncBulk, null);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean asyncBulk,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            Long customStatusId,
            CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, requesterId, type, customStatusId, null, request);
    }


    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean asyncBulk,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, requesterId, type, null, null, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean asyncBulk,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, requesterId, type, null);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean asyncBulk,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, null, null, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean asyncBulk,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, null, null, null);
    }
    @Tool(description = "Get status and progress of an asynchronous Zendesk background job by its job ID")
    public JobStatusResponse getJobStatus(
            @ToolArg(description = "The job status ID") String jobId
    ) {
        log.info("MCP Tool called: getJobStatus(id='{}')", jobId);
        return jobStatusClient.showJobStatus(jobId).block();
    }

    @Tool(description = "List all active ticket fields configured in Zendesk")
    public TicketFieldsResponse listTicketFields() {
        log.info("MCP Tool called: listTicketFields()");
        return ticketClient.listTicketFields(null, true).block();
    }

    @Tool(description = "Get details of a specific Zendesk ticket field by its numeric ID")
    public TicketFieldResponse getTicketField(
            @ToolArg(description = "The numeric ticket field ID") Long ticketFieldId
    ) {
        log.info("MCP Tool called: getTicketField(id={})", ticketFieldId);
        return ticketClient.showTicketField(ticketFieldId).block();
    }

    @Tool(description = "Get full audit event history for a ticket, including field changes, comments, notifications, and trigger/business rule executions (via via.channel='rule', via.source.rel='trigger', via.source.from.title)")
    public TicketAuditsResponse getTicketAudits(
            @ToolArg(description = "The numeric ticket ID") Long ticketId
    ) {
        log.info("MCP Tool called: getTicketAudits(id={})", ticketId);
        return ticketClient.listAuditsForTicket(ticketId).block();
    }


    private Long resolveCustomStatusId(Long customStatusId, @Nullable CallToolRequest request) {
        return ToolValidationSupport.resolveCustomStatusId(customStatusId, request);
    }

    private void validateCustomStatusBounds(Long customStatusId) {
        ToolValidationSupport.validateCustomStatusBounds(customStatusId);
    }

    private Long resolveTicketFormId(Long ticketFormId, @Nullable CallToolRequest request) {
        return ToolValidationSupport.resolveTicketFormId(ticketFormId, request);
    }

    private void validateTicketFormBounds(Long ticketFormId) {
        ToolValidationSupport.validateTicketFormBounds(ticketFormId);
    }

    private TicketForm validateTicketForm(Long ticketFormId) {
        return metadataService.validateTicketForm(ticketFormId);
    }

    private TicketFieldCustomStatusObject validateCustomStatus(Long customStatusId, @Nullable String targetStatus) {
        return metadataService.validateCustomStatus(customStatusId, targetStatus);
    }

    private void validateTicketCustomStatusCategoryMatch(Long ticketId, Ticket currentTicket, TicketFieldCustomStatusObject customStatus, Long customStatusId) {
        metadataService.validateTicketCustomStatusCategoryMatch(ticketId, currentTicket, customStatus, customStatusId);
    }

    private void validateCustomStatusForForm(TicketFieldCustomStatusObject customStatus, Long ticketFormId) {
        metadataService.validateCustomStatusForForm(customStatus, ticketFormId);
    }

    private void validateKnownParameters(CallToolRequest request, String toolName, String... knownParams) {
        ToolValidationSupport.validateKnownParameters(request, toolName, knownParams);
    }
}
