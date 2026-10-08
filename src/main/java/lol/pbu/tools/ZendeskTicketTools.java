package lol.pbu.tools;

import io.micronaut.context.annotation.Value;
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
import lol.pbu.model.TicketCreateInputWithTags;
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
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings("java:S107")
public class ZendeskTicketTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTicketTools.class);
    private static final String TYPE_INCIDENT = "incident";
    private static final String PARAM_SUBJECT = "subject";
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
    private static final String PARAM_ADDITIONAL_TAGS = "additionalTags";
    private static final String PARAM_ADDITIONAL_TAGS_SNAKE = "additional_tags";
    private static final String PARAM_REMOVE_TAGS = "removeTags";
    private static final String PARAM_REMOVE_TAGS_SNAKE = "remove_tags";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_CHUNK_SIZE = "chunkSize";
    private static final String PARAM_CHUNK_SIZE_SNAKE = "chunk_size";
    private static final String PARAM_BATCH_SIZE = "batchSize";
    private static final String PARAM_BATCH_SIZE_SNAKE = "batch_size";
    public static final int MAX_TICKET_CHUNK_SIZE = 100;
    public static final int DEFAULT_TICKET_CHUNK_SIZE = 100;
    private static final String COULD_NOT_BE_RETRIEVED = " could not be retrieved. Does it exist?";
    private static final String TARGET_PROBLEM_TICKET_PREFIX = "Target problem ticket #";

    private final TicketClient ticketClient;
    private final AttachmentClient attachmentClient;
    private final JobStatusClient jobStatusClient;
    private final ZendeskMetadataService metadataService;
    private final int defaultChunkSize;

    public ZendeskTicketTools(
            TicketClient ticketClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ZendeskMetadataService metadataService
    ) {
        this(ticketClient, attachmentClient, jobStatusClient, metadataService, DEFAULT_TICKET_CHUNK_SIZE);
    }

    @Inject
    public ZendeskTicketTools(
            TicketClient ticketClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ZendeskMetadataService metadataService,
            @Value("${micronaut.http.services.zendesk.batch-size:100}") @Nullable Integer defaultChunkSize
    ) {
        this.ticketClient = ticketClient;
        this.attachmentClient = attachmentClient;
        this.jobStatusClient = jobStatusClient;
        this.metadataService = metadataService;
        this.defaultChunkSize = resolveConfiguredChunkSize(defaultChunkSize);
    }

    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }

    public ZendeskMetadataService getMetadataService() {
        return metadataService;
    }
    @Tool(description = "Get details of a specific Zendesk ticket by its numeric ID")
    public Mono<TicketResponse> getTicket(@ToolArg(description = "The numeric ticket ID") Long ticketId) {
        log.info("MCP Tool called: getTicket(id={})", ticketId);
        return ticketClient.showTicket(ticketId);
    }

    @Tool(description = "Get details of multiple Zendesk tickets by their numeric IDs using Zendesk bulk show_many API (GET /api/v2/tickets/show_many.json)")
    public Mono<TicketsResponse> getTickets(
            @ToolArg(description = "List of numeric ticket IDs to retrieve") List<Long> ticketIds,
            @ToolArg(description = "Optional batch chunk size (max 100). Defaults to 100 or ZENDESK_BATCH_SIZE.") @Nullable Integer chunkSize,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: getTickets(ids={}, chunkSize={})", ticketIds, chunkSize);
        validateKnownParameters(request, "getTickets", "ticketIds", PARAM_CHUNK_SIZE, PARAM_CHUNK_SIZE_SNAKE, PARAM_BATCH_SIZE, PARAM_BATCH_SIZE_SNAKE);
        List<Long> distinctIds = parseDistinctTicketIds(ticketIds);
        if (distinctIds.isEmpty()) {
            return Mono.just(new TicketsResponse(Collections.emptyList(), null, null, null));
        }

        int resolvedChunkSize = resolveChunkSize(chunkSize, request);
        List<List<Long>> chunks = partitionTicketIds(distinctIds, resolvedChunkSize);
        return Flux.fromIterable(chunks)
                .concatMap(this::fetchTicketChunk)
                .collectList()
                .map(allTickets -> validateAndWrapTickets(distinctIds, allTickets));
    }

    public Mono<TicketsResponse> getTickets(List<Long> ticketIds) {
        return getTickets(ticketIds, null, null);
    }

    public Mono<TicketsResponse> getTickets(List<Long> ticketIds, @Nullable Integer chunkSize) {
        return getTickets(ticketIds, chunkSize, null);
    }

    private Flux<Ticket> fetchTicketChunk(List<Long> chunk) {
        Mono<TicketsResponse> multiMono = null;
        try {
            multiMono = ticketClient.showMultipleTickets(chunk);
        } catch (Exception e) {
            log.debug("showMultipleTickets invocation failed, using fallback: {}", e.getMessage());
        }
        if (multiMono != null) {
            return multiMono
                    .filter(resp -> resp != null && resp.getTickets() != null)
                    .flatMapMany(resp -> Flux.fromIterable(resp.getTickets()));
        }
        return fetchTicketsIndividually(chunk);
    }

    private Flux<Ticket> fetchTicketsIndividually(List<Long> distinctIds) {
        return Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> {
                    Mono<TicketResponse> showMono = ticketClient.showTicket(id);
                    if (showMono == null) {
                        return Mono.empty();
                    }
                    return showMono
                            .map(TicketResponse::getTicket)
                            .onErrorMap(e -> new RuntimeException(String.format("Failed to fetch ticket %d: [%s] %s", id, e.getClass().getSimpleName(), e.getMessage())))
                            .switchIfEmpty(Mono.error(new RuntimeException(String.format("Failed to fetch ticket %d: [EmptyResult] Ticket not found", id))));
                }, 10);
    }

    private TicketsResponse validateAndWrapTickets(List<Long> distinctIds, List<Ticket> allTickets) {
        Set<Long> foundIds = allTickets.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(Ticket::getId)
                .collect(Collectors.toSet());
        List<Long> missingIds = distinctIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            if (missingIds.size() == 1) {
                throw new IllegalStateException(String.format("Failed to fetch ticket %d: [EmptyResult] Ticket not found", missingIds.get(0)));
            }
            String missingStr = missingIds.stream().map(Object::toString).collect(Collectors.joining(", "));
            throw new IllegalStateException(String.format("Failed to fetch tickets [%s]: [EmptyResult] Tickets not found", missingStr));
        }
        return new TicketsResponse(allTickets, null, null, allTickets.size());
    }

    private static int resolveConfiguredChunkSize(@Nullable Integer configured) {
        if (configured == null || configured <= 0) {
            return DEFAULT_TICKET_CHUNK_SIZE;
        }
        return Math.min(configured, MAX_TICKET_CHUNK_SIZE);
    }

    private int resolveChunkSize(@Nullable Integer chunkSize, @Nullable CallToolRequest request) {
        Integer requested = chunkSize;
        if (requested == null && request != null && request.arguments() != null) {
            requested = extractChunkSizeFromArgs(request.arguments());
        }
        if (requested != null && requested > 0) {
            return Math.min(requested, MAX_TICKET_CHUNK_SIZE);
        }
        return this.defaultChunkSize;
    }

    @Nullable
    private Integer extractChunkSizeFromArgs(Map<String, Object> arguments) {
        Object val = arguments.get(PARAM_CHUNK_SIZE);
        if (val == null) {
            val = arguments.get(PARAM_CHUNK_SIZE_SNAKE);
        }
        if (val == null) {
            val = arguments.get(PARAM_BATCH_SIZE);
        }
        if (val == null) {
            val = arguments.get(PARAM_BATCH_SIZE_SNAKE);
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(val.toString().trim());
            } catch (NumberFormatException _) {
                log.warn("Invalid chunkSize parameter format: {}", val);
            }
        }
        return null;
    }

    private List<List<Long>> partitionTicketIds(List<Long> list, int size) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(list.size(), i + size)));
        }
        return chunks;
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
    public Mono<TicketsResponse> listTickets() {
        log.info("MCP Tool called: listTickets()");
        return ticketClient.listTickets(null);
    }

    @Tool(description = "Get ticket count information from Zendesk")
    public Mono<TicketCountResponse> getTicketCount() {
        log.info("MCP Tool called: getTicketCount()");
        return ticketClient.getTicketCount();
    }

    Mono<List<String>> resolveUploadTokens(List<String> uploadTokens, List<String> attachmentFilePaths) {
        List<String> tokens = new ArrayList<>();
        if (uploadTokens != null) {
            tokens.addAll(uploadTokens);
        }
        if (attachmentFilePaths == null || attachmentFilePaths.isEmpty()) {
            return Mono.just(tokens);
        }
        List<String> validPaths = attachmentFilePaths.stream()
                .filter(StringUtils::isNotEmpty)
                .toList();
        if (validPaths.isEmpty()) {
            return Mono.just(tokens);
        }
        return Flux.fromIterable(validPaths)
                .concatMap(filePath -> uploadAttachment(filePath, null))
                .filter(uploadResp -> uploadResp != null && uploadResp.getUpload() != null && uploadResp.getUpload().getToken() != null)
                .map(uploadResp -> uploadResp.getUpload().getToken())
                .collectList()
                .map(uploadedTokens -> {
                    List<String> allTokens = new ArrayList<>(tokens);
                    allTokens.addAll(uploadedTokens);
                    return allTokens;
                });
    }

    @Tool(description = "Create a new Zendesk ticket. To atomically link this ticket as an incident to an existing problem ticket in a single operation, provide problemId (which automatically sets type to 'incident').")
    public Mono<TicketResponse> createTicket(
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
            @ToolArg(description = "Optional tags to add to the ticket without removing existing ones") @Nullable List<String> additionalTags,
            @ToolArg(description = "Optional tags to remove from the ticket") @Nullable List<String> removeTags,
            @ToolArg(description = "Optional tags to set on the ticket. WARNING: Destructive — replaces all existing tags on the ticket with this set.") @Nullable List<String> tags,
            @ToolArg(description = "Optional ID of the parent problem ticket to link this incident to (automatically sets ticket type to incident)") @Nullable Long problemId,
            CallToolRequest request
    ) {
        log.info("MCP Tool called: createTicket(subject='{}')", subject);
        validateKnownParameters(request, "createTicket", PARAM_SUBJECT, PARAM_COMMENT, PARAM_IS_PUBLIC, PARAM_PRIORITY, PARAM_STATUS, PARAM_UPLOAD_TOKENS, PARAM_ATTACHMENT_FILE_PATHS, PARAM_CUSTOM_FIELDS, PARAM_REQUESTER_ID, PARAM_TYPE, PARAM_DESCRIPTION, PARAM_CUSTOM_STATUS_ID, PARAM_CUSTOM_STATUS_ID_SNAKE, PARAM_TICKET_FORM_ID, PARAM_TICKET_FORM_ID_SNAKE, PARAM_ADDITIONAL_TAGS, PARAM_ADDITIONAL_TAGS_SNAKE, PARAM_REMOVE_TAGS, PARAM_REMOVE_TAGS_SNAKE, PARAM_TAGS, PARAM_PROBLEM_ID);

        final Long resolvedProblemId = resolveProblemId(problemId, request);
        validateProblemTypeConflict(resolvedProblemId, type);

        final List<String> resolvedAdditionalTags = ToolValidationSupport.resolveAndValidateTags(additionalTags, PARAM_ADDITIONAL_TAGS, PARAM_ADDITIONAL_TAGS_SNAKE, request);
        final List<String> resolvedRemoveTags = ToolValidationSupport.resolveAndValidateTags(removeTags, PARAM_REMOVE_TAGS, PARAM_REMOVE_TAGS_SNAKE, request);
        final List<String> resolvedTags = ToolValidationSupport.resolveAndValidateTags(tags, PARAM_TAGS, null, request);

        String initialComment = resolveInitialComment(comment, request);
        return validateProblemTarget(resolvedProblemId)
                .then(resolveUploadTokens(uploadTokens, attachmentFilePaths))
                .flatMap(tokens -> {
                    TicketCreateInputWithTags input = buildTicketCreateInput(
                            subject, initialComment, isPublic, tokens,
                            priority, status, customFields, requesterId, type,
                            resolvedProblemId, ticketFormId, customStatusId,
                            resolvedTags, resolvedAdditionalTags, resolvedRemoveTags, request
                    );
                    return ticketClient.createTicket(new TicketCreateRequest(input));
                });
    }

    private TicketCreateInputWithTags buildTicketCreateInput(
            String subject,
            String initialComment,
            Boolean isPublic,
            List<String> tokens,
            @Nullable String priority,
            @Nullable String status,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long resolvedProblemId,
            @Nullable Long ticketFormId,
            @Nullable Long customStatusId,
            @Nullable List<String> resolvedTags,
            @Nullable List<String> resolvedAdditionalTags,
            @Nullable List<String> resolvedRemoveTags,
            @Nullable CallToolRequest request
    ) {
        validateCreateTicketInput(subject, initialComment, isPublic, tokens);

        TicketComment ticketComment = new TicketComment().setBody(initialComment);
        ticketComment.setIsPublic(isPublic);
        if (!tokens.isEmpty()) {
            ticketComment.setUploads(tokens);
        }
        TicketCreateInputWithTags input = new TicketCreateInputWithTags(ticketComment);
        input.setSubject(subject);
        input.setRawSubject(subject);

        applyPriorityAndStatus(input, priority, status);
        List<TicketCustomField> parsedCustomFields = parseCustomFields(customFields);
        if (!parsedCustomFields.isEmpty()) {
            input.setCustomFields(parsedCustomFields);
        }
        applyRequesterAndType(input, requesterId, type);
        applyProblemLink(input, resolvedProblemId);

        final Long resolvedTicketFormId = resolveTicketFormId(ticketFormId, request);
        applyTicketForm(input, resolvedTicketFormId);

        final Long resolvedCustomStatusId = resolveCustomStatusId(customStatusId, request);
        if (resolvedCustomStatusId != null) {
            validateAndApplyCustomStatus(input, resolvedCustomStatusId, resolvedTicketFormId, status);
        }

        applyCreateTags(input, resolvedTags, resolvedAdditionalTags, resolvedRemoveTags);
        return input;
    }

    private void applyProblemLink(TicketCreateInput input, @Nullable Long resolvedProblemId) {
        if (resolvedProblemId != null) {
            input.setType(TicketUpdateInputType.INCIDENT);
            input.setProblemId(resolvedProblemId);
        }
    }

    private void applyTicketForm(TicketCreateInputWithTags input, @Nullable Long resolvedTicketFormId) {
        if (resolvedTicketFormId != null) {
            validateTicketFormBounds(resolvedTicketFormId);
            validateTicketForm(resolvedTicketFormId);
            input.setTicketFormId(resolvedTicketFormId);
        }
    }

    private void applyCreateTags(TicketCreateInputWithTags input, @Nullable List<String> tags, @Nullable List<String> additionalTags, @Nullable List<String> removeTags) {
        if (tags != null) {
            input.setTags(tags);
        }
        if (additionalTags != null) {
            input.setAdditionalTags(additionalTags);
        }
        if (removeTags != null) {
            input.setRemoveTags(removeTags);
        }
    }

    public Mono<TicketResponse> createTicket(
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
            @Nullable Long ticketFormId,
            @Nullable List<String> additionalTags,
            @Nullable List<String> removeTags,
            @Nullable List<String> tags,
            CallToolRequest request
    ) {
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, customStatusId, ticketFormId, additionalTags, removeTags, tags, null, request);
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
            if (requesterId <= 0) {
                throw new IllegalArgumentException("requesterId must be a positive integer, got: " + requesterId);
            }
            input.setRequesterId(requesterId);
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
        String targetStatus = (status != null && !status.isBlank()) ? status : null;
        TicketFieldCustomStatusObject validated = validateCustomStatus(resolvedCustomStatusId, targetStatus);
        if (resolvedTicketFormId != null && validated != null) {
            validateCustomStatusForForm(validated, resolvedTicketFormId);
        }
        input.setCustomStatusId(resolvedCustomStatusId);
    }

    public Mono<TicketResponse> createTicket(
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
            @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, customStatusId, ticketFormId, null, null, null, request);
    }

    public Mono<TicketResponse> createTicket(
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


    public Mono<TicketResponse> createTicket(
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

    public Mono<TicketResponse> createTicket(
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

    public Mono<TicketResponse> createTicket(
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
            Long ticketFormId,
            List<String> additionalTags,
            List<String> removeTags,
            List<String> tags,
            @Nullable String subject
    ) {
        TicketUpdateInputWithForm input = new TicketUpdateInputWithForm();
        applyUpdateTagsAndForm(input, ticketFormId, additionalTags, removeTags, tags);
        populateInputFromParams(input, comment, status, priority, isPublic, tokens, customFields);
        applyUpdateSubject(input, subject);
        applyUpdateRequesterId(input, requesterId);
        applyUpdateType(input, type, convertToIncident);

        if (customStatusId != null) {
            input.setCustomStatusId(customStatusId);
        }
        return input;
    }

    private void applyUpdateTagsAndForm(TicketUpdateInputWithForm input, @Nullable Long ticketFormId, @Nullable List<String> additionalTags, @Nullable List<String> removeTags, @Nullable List<String> tags) {
        if (ticketFormId != null) {
            input.setTicketFormId(ticketFormId);
        }
        if (additionalTags != null) {
            input.setAdditionalTags(additionalTags);
        }
        if (removeTags != null) {
            input.setRemoveTags(removeTags);
        }
        if (tags != null) {
            input.setTags(tags);
        }
    }

    private void applyUpdateSubject(TicketUpdateInput input, @Nullable String subject) {
        if (subject != null) {
            if (subject.isBlank()) {
                throw new IllegalArgumentException("Ticket 'subject' cannot be empty.");
            }
            input.setSubject(subject);
        }
    }

    private void applyUpdateRequesterId(TicketUpdateInput input, @Nullable Long requesterId) {
        if (requesterId != null) {
            if (requesterId <= 0) {
                throw new IllegalArgumentException("requesterId must be a positive integer, got: " + requesterId);
            }
            input.setRequesterId(requesterId);
        }
    }

    private void applyUpdateType(TicketUpdateInput input, @Nullable String type, @Nullable Boolean convertToIncident) {
        if (type != null) {
            if (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty()) {
                input.setType(null);
            } else {
                input.setType(parseTicketType(type));
            }
        } else if (Boolean.TRUE.equals(convertToIncident)) {
            input.setType(TicketUpdateInputType.INCIDENT);
        }
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
            Long ticketFormId,
            List<String> additionalTags,
            List<String> removeTags,
            List<String> tags
    ) {
        return buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident, customStatusId, ticketFormId, additionalTags, removeTags, tags, null);
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
        return buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident, customStatusId, ticketFormId, null, null, null);
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

    private Mono<Void> validateProblemTarget(Long problemId) {
        if (problemId == null) return Mono.empty();
        if (problemId <= 0) {
            return Mono.error(new IllegalArgumentException("problemId must be a positive integer, got: " + problemId));
        }
        return ticketClient.showTicket(problemId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(TARGET_PROBLEM_TICKET_PREFIX + problemId + COULD_NOT_BE_RETRIEVED)))
                .flatMap(resp -> {
                    Ticket problemTicket = resp != null ? resp.getTicket() : null;
                    if (problemTicket == null) {
                        return Mono.error(new IllegalArgumentException(TARGET_PROBLEM_TICKET_PREFIX + problemId + COULD_NOT_BE_RETRIEVED));
                    }
                    if (problemTicket.getType() != TicketType.PROBLEM) {
                        return Mono.error(new IllegalArgumentException("Target ticket #" + problemId + " is not of type 'problem'"));
                    }
                    return Mono.<Void>empty();
                })
                .onErrorResume(e -> {
                    if (e instanceof IllegalArgumentException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new IllegalArgumentException(TARGET_PROBLEM_TICKET_PREFIX + problemId + COULD_NOT_BE_RETRIEVED, e));
                });
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
        input.setProblemId(problemId);
    }

    @Tool(description = "Update an existing Zendesk ticket with a subject, comment, status, priority, attachments, tags, custom status, ticket form, or link to a problem ticket")
    public Mono<TicketUpdateResponse> updateTicket(
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
            @ToolArg(description = "Optional requester ID (Zendesk user ID). Note: Zendesk restricts updating requesterId post-creation in many account configurations; prefer setting requesterId during createTicket.") @Nullable Long requesterId,
            @ToolArg(description = "Optional ticket type: 'problem', 'incident', 'question', 'task', or empty/none to unset") @Nullable String type,
            @ToolArg(description = "Optional numeric ID of a custom ticket status (from listCustomStatuses)") @Nullable Long customStatusId,
            @ToolArg(description = "Optional numeric ticket form ID to change the form used for this ticket") @Nullable Long ticketFormId,
            @ToolArg(description = "Optional tags to add to the ticket without removing existing ones") @Nullable List<String> additionalTags,
            @ToolArg(description = "Optional tags to remove from the ticket") @Nullable List<String> removeTags,
            @ToolArg(description = "Optional tags to set on the ticket. WARNING: Destructive — replaces all existing tags on the ticket with this set.") @Nullable List<String> tags,
            @ToolArg(description = "Optional updated subject line for the ticket") @Nullable String subject,
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
                .additionalTags(additionalTags)
                .removeTags(removeTags)
                .tags(tags)
                .subject(subject)
                .build();
        return updateTicket(ticketId, options, request);
    }

    public Mono<TicketUpdateResponse> updateTicket(
            Long ticketId,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            @Nullable List<String> additionalTags,
            @Nullable List<String> removeTags,
            @Nullable List<String> tags,
            CallToolRequest request
    ) {
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, additionalTags, removeTags, tags, null, request);
    }

    public Mono<TicketUpdateResponse> updateTicket(
            Long ticketId,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, null, null, null, request);
    }

    private record PreparedTicketMutation(
            TicketMutationOptions resolvedOptions,
            @Nullable Long resolvedTicketFormId,
            @Nullable Long resolvedCustomStatusId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus,
            boolean isTypeUnset,
            @Nullable TicketUpdateInputType parsedType,
            List<TicketCustomField> parsedCustomFields
    ) {}

    private PreparedTicketMutation prepareTicketMutation(TicketMutationOptions opt, @Nullable CallToolRequest request) {
        validateProblemTypeConflict(opt.problemId(), opt.type());

        final String resolvedSubject = resolveSubject(opt.subject(), request);
        if (resolvedSubject != null && resolvedSubject.isBlank()) {
            throw new IllegalArgumentException("Ticket 'subject' cannot be empty.");
        }

        final List<String> resolvedAdditionalTags = ToolValidationSupport.resolveAndValidateTags(opt.additionalTags(), PARAM_ADDITIONAL_TAGS, PARAM_ADDITIONAL_TAGS_SNAKE, request);
        final List<String> resolvedRemoveTags = ToolValidationSupport.resolveAndValidateTags(opt.removeTags(), PARAM_REMOVE_TAGS, PARAM_REMOVE_TAGS_SNAKE, request);
        final List<String> resolvedTags = ToolValidationSupport.resolveAndValidateTags(opt.tags(), PARAM_TAGS, null, request);

        TicketMutationOptions resolvedOpt = opt.toBuilder()
                .subject(resolvedSubject)
                .additionalTags(resolvedAdditionalTags)
                .removeTags(resolvedRemoveTags)
                .tags(resolvedTags)
                .build();

        final Long resolvedTicketFormId = resolveAndValidateTicketFormId(resolvedOpt.ticketFormId(), request);
        final Long resolvedCustomStatusId = resolveCustomStatusId(resolvedOpt.customStatusId(), request);
        TicketFieldCustomStatusObject validatedCustomStatus = validateCustomStatusForMutation(resolvedCustomStatusId, resolvedTicketFormId, resolvedOpt.status());

        final boolean isTypeUnset = isTypeUnset(resolvedOpt.type());
        final TicketUpdateInputType parsedType = (resolvedOpt.type() != null && !isTypeUnset) ? parseTicketType(resolvedOpt.type()) : null;
        List<TicketCustomField> parsedCustomFields = parseCustomFields(resolvedOpt.customFields());

        return new PreparedTicketMutation(resolvedOpt, resolvedTicketFormId, resolvedCustomStatusId, validatedCustomStatus, isTypeUnset, parsedType, parsedCustomFields);
    }

    public Mono<TicketUpdateResponse> updateTicket(
            Long ticketId,
            TicketMutationOptions options,
            @Nullable CallToolRequest request
    ) {
        TicketMutationOptions opt = options != null ? options : TicketMutationOptions.builder().build();
        log.info("MCP Tool called: updateTicket(id={})", ticketId);
        validateKnownParameters(request, "updateTicket", "ticketId", PARAM_COMMENT, PARAM_STATUS, PARAM_PRIORITY, PARAM_IS_PUBLIC, PARAM_UPLOAD_TOKENS, PARAM_ATTACHMENT_FILE_PATHS, PARAM_PROBLEM_ID, PARAM_CONVERT_TO_INCIDENT, PARAM_CUSTOM_FIELDS, PARAM_REQUESTER_ID, PARAM_TYPE, PARAM_CUSTOM_STATUS_ID, PARAM_CUSTOM_STATUS_ID_SNAKE, PARAM_TICKET_FORM_ID, PARAM_TICKET_FORM_ID_SNAKE, PARAM_ADDITIONAL_TAGS, PARAM_ADDITIONAL_TAGS_SNAKE, PARAM_REMOVE_TAGS, PARAM_REMOVE_TAGS_SNAKE, PARAM_TAGS, PARAM_SUBJECT);

        PreparedTicketMutation prep = prepareTicketMutation(opt, request);

        return validateProblemTarget(prep.resolvedOptions().problemId())
                .then(resolveUploadTokens(prep.resolvedOptions().uploadTokens(), prep.resolvedOptions().attachmentFilePaths()))
                .flatMap(tokens -> {
                    TicketUpdateInput input = buildTicketUpdateInput(prep.resolvedOptions().comment(), prep.resolvedOptions().status(), prep.resolvedOptions().priority(), prep.resolvedOptions().isPublic(), tokens, prep.parsedCustomFields(), prep.resolvedOptions().requesterId(), prep.resolvedOptions().type(), prep.resolvedOptions().convertToIncident(), prep.resolvedCustomStatusId(), prep.resolvedTicketFormId(), prep.resolvedOptions().additionalTags(), prep.resolvedOptions().removeTags(), prep.resolvedOptions().tags(), prep.resolvedOptions().subject());
                    return resolveTicketUpdateInput(ticketId, prep.resolvedOptions(), input, prep.parsedType(), prep.isTypeUnset(), prep.resolvedCustomStatusId(), prep.resolvedTicketFormId(), prep.validatedCustomStatus())
                            .flatMap(resolvedInput -> ticketClient.updateTicket(ticketId, new TicketUpdateRequest(resolvedInput)));
                });
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

    public Mono<TicketUpdateResponse> updateTicket(
            Long ticketId,
            TicketMutationOptions options
    ) {
        return updateTicket(ticketId, options, null);
    }

    public Mono<TicketUpdateResponse> updateTicket(
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

    public Mono<TicketUpdateResponse> updateTicket(
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

    public Mono<TicketUpdateResponse> updateTicket(
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

    public Mono<TicketUpdateResponse> updateTicket(
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

    static final Set<String> SENSITIVE_ROOT_DIRS = Set.of(
            "etc", "proc", "sys", "dev", "boot", "root", "run", "windows", "winnt"
    );

    static final Set<String> SENSITIVE_DIR_NAMES = Set.of(
            ".ssh", ".aws", ".gnupg", ".gpg", ".kube", ".docker", ".azure", ".git"
    );

    static final Set<String> SENSITIVE_EXACT_FILENAMES = Set.of(
            "id_rsa", "id_rsa.pub",
            "id_ed25519", "id_ed25519.pub",
            "id_ecdsa", "id_ecdsa.pub",
            "id_dsa", "id_dsa.pub",
            "authorized_keys", "known_hosts",
            ".bash_history", ".zsh_history", ".history", ".sh_history",
            ".netrc", ".npmrc", ".git-credentials",
            "credentials.json", "client_secret.json",
            "passwd", "shadow", "master.passwd", "sudoers",
            ".htpasswd"
    );

    static boolean isSensitiveFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase();
        if (lower.equals(".env") || lower.startsWith(".env.")) {
            return true;
        }
        if (SENSITIVE_EXACT_FILENAMES.contains(lower)) {
            return true;
        }
        return lower.startsWith("id_") && (lower.endsWith(".pub") || !lower.contains("."));
    }

    static boolean hasSensitiveRootOrSystemPrefix(Path normalized) {
        if (normalized.getNameCount() == 0) {
            return false;
        }
        String firstElement = normalized.getName(0).toString().toLowerCase();
        if (SENSITIVE_ROOT_DIRS.contains(firstElement)) {
            return true;
        }
        if (normalized.getNameCount() > 1) {
            if ("var".equalsIgnoreCase(firstElement) && "run".equalsIgnoreCase(normalized.getName(1).toString())) {
                return true;
            }
            if ("private".equalsIgnoreCase(firstElement)) {
                String secondElement = normalized.getName(1).toString().toLowerCase();
                if (SENSITIVE_ROOT_DIRS.contains(secondElement)) {
                    return true;
                }
                return normalized.getNameCount() > 2
                        && "var".equalsIgnoreCase(secondElement)
                        && "run".equalsIgnoreCase(normalized.getName(2).toString());
            }
        }
        return false;
    }

    static boolean hasSensitiveDirectory(Path normalized) {
        for (int i = 0; i < normalized.getNameCount(); i++) {
            String element = normalized.getName(i).toString().toLowerCase();
            if (SENSITIVE_DIR_NAMES.contains(element)) {
                return true;
            }
        }
        return false;
    }

    static boolean isSensitiveSystemPath(Path path) {
        if (path == null) {
            return false;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (hasSensitiveRootOrSystemPrefix(normalized) || hasSensitiveDirectory(normalized)) {
            return true;
        }
        Path fileName = normalized.getFileName();
        return fileName != null && isSensitiveFilename(fileName.toString());
    }

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
        if (isSensitiveSystemPath(path)) {
            throw new IllegalArgumentException("Access denied: Uploading sensitive system or credential files is prohibited: " + filePath);
        }
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
        Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve real path for " + filePath + ": " + e.getMessage(), e);
        }
        if (isSensitiveSystemPath(realPath)) {
            throw new IllegalArgumentException("Access denied: Uploading sensitive system or credential files is prohibited: " + filePath);
        }
        try {
            long size = Files.size(realPath);
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
    public Mono<AttachmentUploadResponse> uploadAttachment(
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

        return attachmentClient.uploadAttachment(targetFilename, contentType, bytes);
    }

    public Mono<AttachmentUploadResponse> uploadAttachment(String filePath, @Nullable String filename) {
        return uploadAttachment(filePath, filename, null);
    }


    @Tool(description = "Batch update multiple Zendesk tickets by their numeric IDs with a subject, comment, status, priority, attachments, tags, custom status, ticket form, or link to a problem ticket. Queues an async bulk job in Zendesk (PUT /api/v2/tickets/update_many) returning JobStatus by default, or performs concurrent immediate updates if asyncBulk is false. For async jobs, use getJobStatus(jobId) to track progress until completed.")
    public Mono<BatchUpdateResponse> batchUpdateTickets(
            @ToolArg(description = "List of numeric ticket IDs to update") List<Long> ticketIds,
            @ToolArg(description = "Comment text to add to the tickets") @Nullable String comment,
            @ToolArg(description = "New status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "New priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Required if a comment or attachment is provided. Whether the comment is public (true) or private internal note (false)") @Nullable Boolean isPublic,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths,
            @ToolArg(description = "If true (default), queues an async bulk job in Zendesk (PUT /api/v2/tickets/update_many) returning JobStatus. If false, updates tickets concurrently via Reactor returning immediate per-ticket results.") @Nullable Boolean asyncBulk,
            @ToolArg(description = "Optional ID of the parent problem ticket to link these incidents to") @Nullable Long problemId,
            @ToolArg(description = "Required if setting problemId on tickets that are not currently incidents. Set to true to explicitly convert them.") @Nullable Boolean convertToIncident,
            @ToolArg(description = "Optional custom fields as a list of objects containing 'id' and 'value', e.g. [{'id': 1234, 'value': 'foo'}]") @Nullable List<Map<String, Object>> customFields,
            @ToolArg(description = "Optional requester ID (Zendesk user ID). Note: Zendesk restricts updating requesterId post-creation in many account configurations; prefer setting requesterId during createTicket.") @Nullable Long requesterId,
            @ToolArg(description = "Optional ticket type: 'problem', 'incident', 'question', 'task', or empty/none to unset") @Nullable String type,
            @ToolArg(description = "Optional numeric ID of a custom ticket status (from listCustomStatuses)") @Nullable Long customStatusId,
            @ToolArg(description = "Optional numeric ticket form ID to change the form used for these tickets") @Nullable Long ticketFormId,
            @ToolArg(description = "Optional tags to add to the tickets without removing existing ones") @Nullable List<String> additionalTags,
            @ToolArg(description = "Optional tags to remove from the tickets") @Nullable List<String> removeTags,
            @ToolArg(description = "Optional tags to set on the tickets. WARNING: Destructive — replaces all existing tags on the tickets with this set.") @Nullable List<String> tags,
            @ToolArg(description = "Optional updated subject line for the tickets") @Nullable String subject,
            @ToolArg(description = "Optional batch chunk size (max 100). Defaults to 100 or ZENDESK_BATCH_SIZE.") @Nullable Integer chunkSize,
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
                .additionalTags(additionalTags)
                .removeTags(removeTags)
                .tags(tags)
                .subject(subject)
                .build();
        return batchUpdateTickets(ticketIds, options, asyncBulk, chunkSize, request);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Boolean asyncBulk,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            @Nullable List<String> additionalTags,
            @Nullable List<String> removeTags,
            @Nullable List<String> tags,
            CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, additionalTags, removeTags, tags, null, null, request);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Boolean asyncBulk,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            @Nullable List<String> additionalTags,
            @Nullable List<String> removeTags,
            @Nullable List<String> tags,
            @Nullable Integer chunkSize,
            CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, additionalTags, removeTags, tags, null, chunkSize, request);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Boolean asyncBulk,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, asyncBulk, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, null, null, null, null, null, request);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options,
            @Nullable Boolean asyncBulk,
            @Nullable CallToolRequest request
    ) {
        return batchUpdateTickets(ticketIds, options, asyncBulk, null, request);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options,
            @Nullable Boolean asyncBulk,
            @Nullable Integer chunkSize,
            @Nullable CallToolRequest request
    ) {
        TicketMutationOptions opt = options != null ? options : TicketMutationOptions.builder().build();
        log.info("MCP Tool called: batchUpdateTickets(ids={}, asyncBulk={}, chunkSize={})", ticketIds, asyncBulk, chunkSize);
        validateKnownParameters(request, "batchUpdateTickets", "ticketIds", PARAM_COMMENT, PARAM_STATUS, PARAM_PRIORITY, PARAM_IS_PUBLIC, PARAM_UPLOAD_TOKENS, PARAM_ATTACHMENT_FILE_PATHS, "asyncBulk", PARAM_PROBLEM_ID, PARAM_CONVERT_TO_INCIDENT, PARAM_CUSTOM_FIELDS, PARAM_REQUESTER_ID, PARAM_TYPE, PARAM_CUSTOM_STATUS_ID, PARAM_CUSTOM_STATUS_ID_SNAKE, PARAM_TICKET_FORM_ID, PARAM_TICKET_FORM_ID_SNAKE, PARAM_ADDITIONAL_TAGS, PARAM_ADDITIONAL_TAGS_SNAKE, PARAM_REMOVE_TAGS, PARAM_REMOVE_TAGS_SNAKE, PARAM_TAGS, PARAM_SUBJECT, PARAM_CHUNK_SIZE, PARAM_CHUNK_SIZE_SNAKE, PARAM_BATCH_SIZE, PARAM_BATCH_SIZE_SNAKE);
        List<Long> distinctIds = parseDistinctTicketIds(ticketIds);
        if (distinctIds.isEmpty()) {
            return Mono.just(new BatchUpdateResponse(null, null, Collections.emptyList()));
        }

        int resolvedChunkSize = resolveChunkSize(chunkSize, request);
        PreparedTicketMutation prep = prepareTicketMutation(opt, request);

        return validateProblemTarget(prep.resolvedOptions().problemId())
                .then(resolveUploadTokens(prep.resolvedOptions().uploadTokens(), prep.resolvedOptions().attachmentFilePaths()))
                .flatMap(tokens -> {
                    if (!Boolean.FALSE.equals(asyncBulk)) {
                        return executeAsyncBulkBatchUpdate(distinctIds, resolvedChunkSize, prep.resolvedOptions(), tokens, prep.parsedCustomFields(), prep.resolvedCustomStatusId(), prep.resolvedTicketFormId(), prep.validatedCustomStatus(), prep.parsedType(), prep.isTypeUnset());
                    }
                    return executeConcurrentBatchUpdate(distinctIds, prep.resolvedOptions(), tokens, prep.parsedCustomFields(), prep.resolvedCustomStatusId(), prep.resolvedTicketFormId(), prep.validatedCustomStatus(), prep.parsedType(), prep.isTypeUnset());
                });
    }

    private Mono<BatchUpdateResponse> executeAsyncBulkBatchUpdate(
            List<Long> distinctIds,
            int chunkSize,
            TicketMutationOptions options,
            List<String> tokens,
            List<TicketCustomField> parsedCustomFields,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset
    ) {
        return validateCurrentTicketsForBulkAsync(distinctIds, chunkSize, options, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus, parsedType, isTypeUnset)
                .then(Mono.defer(() -> {
                    TicketUpdateInput input = buildTicketUpdateInput(options.comment(), options.status(), options.priority(), options.isPublic(), tokens, parsedCustomFields, options.requesterId(), options.type(), options.convertToIncident(), resolvedCustomStatusId, resolvedTicketFormId, options.additionalTags(), options.removeTags(), options.tags(), options.subject());
                    if (options.problemId() != null) {
                        input.setProblemId(options.problemId());
                        input.setType(TicketUpdateInputType.INCIDENT);
                    }
                    return sendBulkUpdateChunks(distinctIds, input, chunkSize)
                            .map(jobStatuses -> new BatchUpdateResponse(jobStatuses.size() == 1 ? jobStatuses.get(0) : null, jobStatuses.isEmpty() ? null : jobStatuses, null));
                }));
    }

    private Mono<Void> validateCurrentTicketsForBulkAsync(
            List<Long> distinctIds,
            int chunkSize,
            TicketMutationOptions options,
            @Nullable Long resolvedCustomStatusId,
            @Nullable Long resolvedTicketFormId,
            @Nullable TicketFieldCustomStatusObject validatedCustomStatus,
            @Nullable TicketUpdateInputType parsedType,
            boolean isTypeUnset
    ) {
        if (!needsCurrentTicket(options, resolvedCustomStatusId, isTypeUnset, parsedType)) {
            return Mono.empty();
        }
        List<List<Long>> chunks = partitionTicketIds(distinctIds, chunkSize);
        return Flux.fromIterable(chunks)
                .concatMap(this::fetchTicketsForValidation)
                .doOnNext(currentTicket -> {
                    TicketUpdateInput testInput = new TicketUpdateInput();
                    validateTicketState(currentTicket.getId(), currentTicket, options, testInput, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus);
                })
                .then();
    }

    private Flux<Ticket> fetchTicketsForValidation(List<Long> chunk) {
        Mono<TicketsResponse> multiMono = null;
        try {
            multiMono = ticketClient.showMultipleTickets(chunk);
        } catch (Exception e) {
            log.debug("showMultipleTickets invocation failed for validation: {}", e.getMessage());
        }
        if (multiMono != null) {
            return multiMono
                    .filter(resp -> resp != null && resp.getTickets() != null)
                    .flatMapMany(resp -> Flux.fromIterable(resp.getTickets()));
        }
        return fallbackFetchTicketsForValidation(chunk);
    }

    private Flux<Ticket> fallbackFetchTicketsForValidation(List<Long> chunk) {
        return Flux.fromIterable(chunk)
                .flatMap(id -> {
                    Mono<TicketResponse> showMono = ticketClient.showTicket(id);
                    if (showMono == null) {
                        return Mono.empty();
                    }
                    return showMono.onErrorResume(e -> Mono.empty())
                            .filter(resp -> resp != null && resp.getTicket() != null)
                            .map(TicketResponse::getTicket);
                });
    }

    private Mono<List<JobStatus>> sendBulkUpdateChunks(List<Long> distinctIds, TicketUpdateInput input, int chunkSize) {
        List<List<Long>> chunks = partitionTicketIds(distinctIds, chunkSize);
        return Flux.fromIterable(chunks)
                .concatMap(chunk -> {
                    String idsStr = chunk.stream().map(Object::toString).collect(Collectors.joining(","));
                    Mono<JobStatusResponse> respMono = ticketClient.updateManyTickets(idsStr, new TicketUpdateRequest(input));
                    if (respMono == null) {
                        return Mono.<JobStatus>empty();
                    }
                    return respMono
                            .filter(jobResponse -> jobResponse != null && jobResponse.getJobStatus() != null)
                            .map(JobStatusResponse::getJobStatus);
                })
                .collectList();
    }

    private Mono<BatchUpdateResponse> executeConcurrentBatchUpdate(
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
        return Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> {
                    TicketUpdateInput input = buildTicketUpdateInput(options.comment(), options.status(), options.priority(), options.isPublic(), tokens, parsedCustomFields, options.requesterId(), options.type(), options.convertToIncident(), resolvedCustomStatusId, resolvedTicketFormId, options.additionalTags(), options.removeTags(), options.tags(), options.subject());
                    return resolveTicketUpdateInput(id, options, input, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus)
                            .flatMap(resolvedInput -> ticketClient.updateTicket(id, new TicketUpdateRequest(resolvedInput)))
                            .map(resp -> new TicketUpdateResult(id, true, resp != null ? resp.getTicket() : null, null))
                            .onErrorResume(e -> {
                                log.warn("Failed to update ticket {}: {}", id, e.getMessage());
                                return Mono.just(new TicketUpdateResult(id, false, null, e.getMessage()));
                            });
                }, 10)
                .collectList()
                .map(results -> new BatchUpdateResponse(null, null, results != null ? results : Collections.emptyList()));
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
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket #" + id + COULD_NOT_BE_RETRIEVED)))
                .flatMap(resp -> {
                    if (resp.getTicket() == null) {
                        return Mono.error(new IllegalArgumentException("Ticket #" + id + COULD_NOT_BE_RETRIEVED));
                    }
                    Ticket currentTicket = resp.getTicket();
                    validateTicketState(id, currentTicket, options, input, parsedType, isTypeUnset, resolvedCustomStatusId, resolvedTicketFormId, validatedCustomStatus);
                    return Mono.just(input);
                });
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options
    ) {
        return batchUpdateTickets(ticketIds, options, null, null);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
            List<Long> ticketIds,
            TicketMutationOptions options,
            @Nullable Boolean asyncBulk
    ) {
        return batchUpdateTickets(ticketIds, options, asyncBulk, null);
    }

    public Mono<BatchUpdateResponse> batchUpdateTickets(
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


    public Mono<BatchUpdateResponse> batchUpdateTickets(
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

    public Mono<BatchUpdateResponse> batchUpdateTickets(
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

    public Mono<BatchUpdateResponse> batchUpdateTickets(
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

    public Mono<BatchUpdateResponse> batchUpdateTickets(
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
    public Mono<JobStatusResponse> getJobStatus(
            @ToolArg(description = "The job status ID") String jobId
    ) {
        log.info("MCP Tool called: getJobStatus(id='{}')", jobId);
        return jobStatusClient.showJobStatus(jobId);
    }

    @Tool(description = "List all active ticket fields configured in Zendesk")
    public Mono<TicketFieldsResponse> listTicketFields() {
        log.info("MCP Tool called: listTicketFields()");
        return ticketClient.listTicketFields(null, true);
    }

    @Tool(description = "Get details of a specific Zendesk ticket field by its numeric ID")
    public Mono<TicketFieldResponse> getTicketField(
            @ToolArg(description = "The numeric ticket field ID") Long ticketFieldId
    ) {
        log.info("MCP Tool called: getTicketField(id={})", ticketFieldId);
        return ticketClient.showTicketField(ticketFieldId);
    }

    @Tool(description = "Get full audit event history for a ticket, including field changes, comments, notifications, and trigger/business rule executions (via via.channel='rule', via.source.rel='trigger', via.source.from.title)")
    public Mono<TicketAuditsResponse> getTicketAudits(
            @ToolArg(description = "The numeric ticket ID") Long ticketId,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: getTicketAudits(id={})", ticketId);
        validateKnownParameters(request, "getTicketAudits", "ticketId");
        if (ticketId == null || ticketId <= 0) {
            throw new IllegalArgumentException("ticketId must be a positive integer, got: " + ticketId);
        }
        return ticketClient.listAuditsForTicket(ticketId)
                .defaultIfEmpty(new TicketAuditsResponse(Collections.emptyList(), 0, null, null));
    }

    public Mono<TicketAuditsResponse> getTicketAudits(Long ticketId) {
        return getTicketAudits(ticketId, null);
    }

    @Nullable
    private Long resolveProblemId(@Nullable Long problemId, @Nullable CallToolRequest request) {
        if (problemId != null) {
            return problemId;
        }
        if (request != null && request.arguments() != null && request.arguments().containsKey(PARAM_PROBLEM_ID)) {
            Object val = request.arguments().get(PARAM_PROBLEM_ID);
            if (val instanceof Number num) {
                return num.longValue();
            }
            if (val != null) {
                try {
                    return Long.parseLong(val.toString().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid problemId: '" + val + "'. Must be a valid numeric ID.", e);
                }
            }
        }
        return null;
    }

    @Nullable
    private String resolveSubject(@Nullable String subject, @Nullable CallToolRequest request) {
        if (subject != null) {
            return subject;
        }
        if (request != null && request.arguments() != null && request.arguments().containsKey(PARAM_SUBJECT)) {
            Object val = request.arguments().get(PARAM_SUBJECT);
            return val != null ? val.toString() : null;
        }
        return null;
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
