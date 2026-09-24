package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Singleton;
import lol.pbu.model.BatchUpdateResponse;
import lol.pbu.model.BatchUpdateResponse.TicketUpdateResult;
import lol.pbu.z4j.client.ArticleClient;
import lol.pbu.z4j.client.AttachmentClient;
import lol.pbu.z4j.client.CategoryClient;
import lol.pbu.z4j.client.CustomObjectRecordsClient;
import lol.pbu.z4j.client.CustomObjectsClient;
import lol.pbu.z4j.client.JobStatusClient;
import lol.pbu.z4j.client.PostClient;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.client.TicketClient;
import lol.pbu.z4j.client.TicketFormsClient;
import lol.pbu.z4j.client.TopicClient;
import lol.pbu.z4j.client.TranslationClient;
import lol.pbu.z4j.client.ViewClient;
import lol.pbu.z4j.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.Arrays;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import lol.pbu.z4j.model.TicketCustomField;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * MCP Tools exposing Zendesk ticket management, forms, custom objects, attachments, search, views, articles, categories, translations, and community operations via z4j.
 */
@Singleton
public class ZendeskTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTools.class);
    private static final String TICKET_FORMS_KEY = "ticket_forms";

    private final TicketClient ticketClient;
    private final SearchClient searchClient;
    private final TicketFormsClient ticketFormsClient;
    private final CustomObjectsClient customObjectsClient;
    private final CustomObjectRecordsClient customObjectRecordsClient;
    private final AttachmentClient attachmentClient;
    private final JobStatusClient jobStatusClient;
    private final ViewClient viewClient;
    private final ArticleClient articleClient;
    private final CategoryClient categoryClient;
    private final TranslationClient translationClient;
    private final TopicClient topicClient;
    private final PostClient postClient;

    public ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ViewClient viewClient,
            ArticleClient articleClient,
            CategoryClient categoryClient,
            TranslationClient translationClient,
            TopicClient topicClient,
            PostClient postClient
    ) {
        this.ticketClient = ticketClient;
        this.searchClient = searchClient;
        this.ticketFormsClient = ticketFormsClient;
        this.customObjectsClient = customObjectsClient;
        this.customObjectRecordsClient = customObjectRecordsClient;
        this.attachmentClient = attachmentClient;
        this.jobStatusClient = jobStatusClient;
        this.viewClient = viewClient;
        this.articleClient = articleClient;
        this.categoryClient = categoryClient;
        this.translationClient = translationClient;
        this.topicClient = topicClient;
        this.postClient = postClient;
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
        if (ticketIds == null || ticketIds.isEmpty()) {
            return new TicketsResponse(Collections.emptyList());
        }

        List<?> rawIds = ticketIds;
        java.util.List<Long> distinctIds = new java.util.ArrayList<>();
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
        distinctIds = distinctIds.stream().distinct().toList();

        List<Ticket> tickets = Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> ticketClient.showTicket(id)
                        .map(TicketResponse::getTicket)
                        .onErrorMap(e -> new RuntimeException(String.format("Failed to fetch ticket %d: [%s] %s", id, e.getClass().getSimpleName(), e.getMessage())))
                        .switchIfEmpty(Mono.error(new RuntimeException(String.format("Failed to fetch ticket %d: [EmptyResult] Ticket not found", id)))), 10)
                .collectList()
                .block();

        return new TicketsResponse(tickets != null ? tickets : Collections.emptyList());
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

    @Tool(description = "Search Zendesk using Zendesk search syntax (e.g. 'type:ticket status:open', 'type:ticket created>2026-01-01'). Supports sideloading related resources (users, organizations, groups) via 'include'.")
    public SearchResponse search(
            @ToolArg(description = "Zendesk search query string") String query,
            @ToolArg(description = "Optional resources to sideload. E.g. 'users,organizations,groups' (auto-wrapped in tickets(...)) or explicit 'tickets(users,organizations)'") @Nullable String include,
            @ToolArg(description = "Maximum number of results to return (default 25)") @Nullable Integer maxResults
    ) {
        if (query != null && query.toLowerCase().contains("problem_id:")) {
            throw new IllegalArgumentException("Zendesk search does not support filtering by problem_id. The search index does not cover this field, and queries will silently return 0 results. If you need to find incidents linked to a problem, you must retrieve tickets individually or use a different discovery mechanism.");
        }
        int limit = (maxResults != null && maxResults > 0) ? maxResults : 25;
        String resolvedInclude = null;
        if (io.micronaut.core.util.StringUtils.isNotEmpty(include)) {
            String trimmed = include.trim();
            if (!trimmed.contains("(")) {
                resolvedInclude = "tickets(" + trimmed + ")";
            } else {
                resolvedInclude = trimmed;
            }
        }
        log.info("MCP Tool called: search(query='{}', include='{}', maxResults={})", query, resolvedInclude, limit);

        SearchResponse accumulatedResponse = new SearchResponse();
        accumulatedResponse.setResults(new java.util.ArrayList<>());
        accumulatedResponse.setUsers(new java.util.ArrayList<>());
        accumulatedResponse.setOrganizations(new java.util.ArrayList<>());
        accumulatedResponse.setGroups(new java.util.ArrayList<>());

        int p = 1;
        while (accumulatedResponse.getResults().size() < limit) {
            SearchResponse pageResponse = searchClient.list(query, resolvedInclude, null, null, p, 100)
                    .block();

            if (pageResponse == null || pageResponse.getResults() == null || pageResponse.getResults().isEmpty()) {
                break;
            }

            accumulatedResponse.getResults().addAll(pageResponse.getResults());
            if (pageResponse.getUsers() != null) accumulatedResponse.getUsers().addAll(pageResponse.getUsers());
            if (pageResponse.getOrganizations() != null) accumulatedResponse.getOrganizations().addAll(pageResponse.getOrganizations());
            if (pageResponse.getGroups() != null) accumulatedResponse.getGroups().addAll(pageResponse.getGroups());

            if (pageResponse.getNextPage() == null) {
                break;
            }
            p++;
        }

        if (accumulatedResponse.getResults().size() > limit) {
            accumulatedResponse.setResults(accumulatedResponse.getResults().subList(0, limit));
        }
        accumulatedResponse.setCount(accumulatedResponse.getResults().size());

        return accumulatedResponse;
    }

    @Tool(description = "Get the count of search results matching a query in Zendesk")
    public SearchResponse searchCount(
            @ToolArg(description = "Zendesk search query string, e.g. 'type:ticket status:open'") String query
    ) {
        if (query != null && query.toLowerCase().contains("problem_id:")) {
            throw new IllegalArgumentException("Zendesk search does not support filtering by problem_id. The search index does not cover this field, and queries will silently return 0 results. If you need to find incidents linked to a problem, you must retrieve tickets individually or use a different discovery mechanism.");
        }
        log.info("MCP Tool called: searchCount(query='{}')", query);
        return searchClient.count(query).block();
    }

    private List<String> resolveUploadTokens(List<String> uploadTokens, List<String> attachmentFilePaths) {
        List<String> tokens = new ArrayList<>();
        if (uploadTokens != null) {
            tokens.addAll(uploadTokens);
        }
        if (attachmentFilePaths != null) {
            for (String filePath : attachmentFilePaths) {
                if (StringUtils.isNotEmpty(filePath)) {
                    try {
                        Path path = Path.of(filePath);
                        byte[] bytes = Files.readAllBytes(path);
                        String contentType = Files.probeContentType(path);
                        if (contentType == null) {
                            contentType = "application/octet-stream";
                        }
                        String filename = path.getFileName().toString();
                        AttachmentUploadResponse uploadResp = attachmentClient.uploadAttachment(filename, contentType, bytes).block();
                        if (uploadResp != null && uploadResp.getUpload() != null && uploadResp.getUpload().getToken() != null) {
                            tokens.add(uploadResp.getUpload().getToken());
                        }
                    } catch (Exception e) {
                        log.error("Failed to upload attachment from {}: {}", filePath, e.getMessage(), e);
                        throw new RuntimeException("Failed to upload attachment from " + filePath + ": " + e.getMessage(), e);
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
            CallToolRequest request
    ) {
        log.info("MCP Tool called: createTicket(subject='{}')", subject);
        validateKnownParameters(request, "createTicket", "subject", "comment", "isPublic", "priority", "status", "uploadTokens", "attachmentFilePaths", "customFields", "requesterId", "type", "description");

        String initialComment = comment;
        if (request != null && request.arguments() != null && request.arguments().containsKey("description")) {
            Object descObj = request.arguments().get("description");
            String desc = descObj != null ? descObj.toString() : null;
            if (StringUtils.isNotEmpty(desc)) {
                if (StringUtils.isNotEmpty(initialComment) && !initialComment.equals(desc)) {
                    throw new IllegalArgumentException("Provide either 'comment' or 'description' for the initial ticket message, not both with different content.");
                }
                if (StringUtils.isEmpty(initialComment)) {
                    initialComment = desc;
                }
            }
        }

        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);
        if (StringUtils.isEmpty(initialComment) && tokens.isEmpty()) {
            throw new IllegalArgumentException("Either 'comment' or 'description' is required when creating a ticket.");
        }

        if (isPublic == null) {
            throw new IllegalArgumentException("isPublic is required when creating a ticket. Set to true for a public initial comment, or false for an internal note.");
        }
        TicketComment ticketComment = new TicketComment().setBody(initialComment);
        ticketComment.setIsPublic(isPublic);
        if (!tokens.isEmpty()) {
            ticketComment.setUploads(tokens);
        }
        TicketCreateInput input = new TicketCreateInput(ticketComment);
        input.setRawSubject(subject);

        if (StringUtils.isNotEmpty(priority)) {
            try {
                input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown priority '{}', ignoring", priority);
            }
        }
        if (StringUtils.isNotEmpty(status)) {
            try {
                input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown status '{}', ignoring", status);
            }
        }
        List<TicketCustomField> parsedCustomFields = parseCustomFields(customFields);
        if (!parsedCustomFields.isEmpty()) {
            input.setCustomFields(parsedCustomFields);
        }
        if (requesterId != null) {
            if (requesterId > Integer.MAX_VALUE || requesterId < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("requesterId " + requesterId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits requester_id on ticket inputs to 32-bit integers.");
            }
            input.setRequesterId(requesterId.intValue());
        }
        if (StringUtils.isNotEmpty(type)) {
            if (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty()) {
                input.setType(null);
            } else {
                TicketUpdateInputType parsedType = parseTicketType(type);
                input.setType(parsedType);
            }
        }

        return ticketClient.createTicket(new TicketCreateRequest(input)).block();
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
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, null, null, null, null);
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
        return createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, null);
    }

    private TicketUpdateInputType parseTicketType(String type) {
        if (type != null) {
            String normalized = type.trim().toLowerCase();
            if ("problem".equals(normalized) || "incident".equals(normalized) || "question".equals(normalized) || "task".equals(normalized)) {
                return TicketUpdateInputType.fromValue(normalized);
            }
        }
        throw new IllegalArgumentException("Invalid ticket type: '" + type + "'. Allowed types are: problem, incident, question, task.");
    }

    private void validateKnownParameters(CallToolRequest request, String toolName, String... knownParams) {
        if (request == null || request.arguments() == null) return;
        Set<String> known = new HashSet<>(Arrays.asList(knownParams));
        if (request.arguments().containsKey("description") && !known.contains("description")) {
            throw new IllegalArgumentException("Ticket description cannot be modified after creation as it is read-only in Zendesk. To add information to an existing ticket, use the 'comment' parameter instead.");
        }
        for (String key : request.arguments().keySet()) {
            if (!known.contains(key)) {
                String hint = "createTicket".equals(toolName)
                        ? "If you meant to set a custom field, use the 'customFields' array parameter."
                        : "If you meant to update a custom field, use the 'customFields' array parameter.";
                throw new IllegalArgumentException("Unrecognized parameter: '" + key + "'. " + hint);
            }
        }
    }

    private void validateTicketTypeChange(Ticket currentTicket, TicketUpdateInputType newType, boolean isTypeUnset) {
        if (currentTicket == null) return;
        if (currentTicket.getType() == TicketType.PROBLEM && Boolean.TRUE.equals(currentTicket.getHasIncidents())) {
            if (isTypeUnset || newType != TicketUpdateInputType.PROBLEM) {
                throw new IllegalArgumentException("ticket #" + currentTicket.getId() + " is a parent problem with linked incidents — reassign or resolve those first");
            }
        }
    }

    private List<TicketCustomField> parseCustomFields(List<Map<String, Object>> customFields) {
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

    private TicketUpdateInput buildInputFromParams(String comment, String status, String priority, Boolean isPublic, List<String> tokens, List<TicketCustomField> customFields) {
        TicketUpdateInput input = new TicketUpdateInput();
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
        if (StringUtils.isNotEmpty(status)) {
            try {
                input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown status '{}', ignoring", status);
            }
        }
        if (StringUtils.isNotEmpty(priority)) {
            try {
                input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
            } catch (Exception _) {
                log.warn("Unknown priority '{}', ignoring", priority);
            }
        }
        if (customFields != null && !customFields.isEmpty()) { input.setCustomFields(customFields); }
        return input;
    }

    private void validateProblemTarget(Long problemId) {
        if (problemId == null) return;
        try {
            Ticket problemTicket = ticketClient.showTicket(problemId).block().getTicket();
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
        
        if (currentType != TicketType.INCIDENT) {
            if (!Boolean.TRUE.equals(convertToIncident)) {
                throw new IllegalArgumentException("Ticket #" + currentTicket.getId() + " is not an incident. Pass convertToIncident=true to explicitly convert it.");
            }
            input.setType(TicketUpdateInputType.INCIDENT);
        }
        
        input.setProblemId(problemId.intValue());
    }

    @Tool(description = "Update an existing Zendesk ticket with a comment, status, priority, attachments, or link to a problem ticket")
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
            CallToolRequest request
    ) {
        log.info("MCP Tool called: updateTicket(id={})", ticketId);
        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);
        validateKnownParameters(request, "updateTicket", "ticketId", "comment", "status", "priority", "isPublic", "uploadTokens", "attachmentFilePaths", "problemId", "convertToIncident", "customFields", "requesterId", "type");
        List<TicketCustomField> parsedCustomFields = parseCustomFields(customFields);
        TicketUpdateInput input = buildInputFromParams(comment, status, priority, isPublic, tokens, parsedCustomFields);
        if (requesterId != null) {
            if (requesterId > Integer.MAX_VALUE || requesterId < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("requesterId " + requesterId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits requester_id on ticket inputs to 32-bit integers.");
            }
            input.setRequesterId(requesterId.intValue());
        }

        final boolean isTypeUnset = type != null && (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty());
        final TicketUpdateInputType parsedType = (type != null && !isTypeUnset) ? parseTicketType(type) : null;
        if (type != null) {
            if (isTypeUnset) {
                input.setType(null);
            } else {
                input.setType(parsedType);
            }
        }
        if (Boolean.TRUE.equals(convertToIncident) && type == null) {
            input.setType(TicketUpdateInputType.INCIDENT);
        }

        validateProblemTarget(problemId);

        if (problemId != null || type != null) {
            TicketResponse showResp = ticketClient.showTicket(ticketId).block();
            if (showResp == null || showResp.getTicket() == null) {
                throw new IllegalArgumentException("Ticket #" + ticketId + " could not be retrieved. Does it exist?");
            }
            Ticket currentTicket = showResp.getTicket();
            if (type != null) {
                validateTicketTypeChange(currentTicket, parsedType, isTypeUnset);
            }
            if (problemId != null) {
                applyProblemIdLogic(currentTicket, input, problemId, convertToIncident);
            }
        }

        return ticketClient.updateTicket(ticketId, new TicketUpdateRequest(input)).block();
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
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, null, null, request);
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
        return updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, null, null, null);
    }
    @Tool(description = "Upload a file from the local file system to Zendesk to obtain an upload token for use in createTicket, updateTicket, or batchUpdateTickets")
    public AttachmentUploadResponse uploadAttachment(
            @ToolArg(description = "The absolute path to the local file to upload") String filePath,
            @ToolArg(description = "Optional filename to use for the attachment. If not provided, the local filename is used.") @Nullable String filename
    ) {
        log.info("MCP Tool called: uploadAttachment(filePath='{}')", filePath);
        try {
            Path path = Path.of(filePath);
            byte[] bytes = Files.readAllBytes(path);
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            String targetFilename = StringUtils.isNotEmpty(filename) ? filename : path.getFileName().toString();
            return attachmentClient.uploadAttachment(targetFilename, contentType, bytes).block();
        } catch (Exception e) {
            log.error("Failed to upload attachment from {}: {}", filePath, e.getMessage(), e);
            throw new RuntimeException("Failed to upload attachment: " + e.getMessage(), e);
        }
    }


    @Tool(description = "Batch update multiple Zendesk tickets by their numeric IDs with a comment, status, priority, attachments, or link to a problem ticket. Supports concurrent immediate updates or Zendesk async bulk jobs.")
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
            CallToolRequest request
    ) {
        log.info("MCP Tool called: batchUpdateTickets(ids={}, asyncBulk={})", ticketIds, asyncBulk);
        validateKnownParameters(request, "batchUpdateTickets", "ticketIds", "comment", "status", "priority", "isPublic", "uploadTokens", "attachmentFilePaths", "asyncBulk", "problemId", "convertToIncident", "customFields", "requesterId", "type");
        if (ticketIds == null || ticketIds.isEmpty()) {
            return new BatchUpdateResponse(null, null, Collections.emptyList());
        }

        List<?> rawIds = ticketIds;
        List<Long> distinctIds = new ArrayList<>();
        for (Object obj : rawIds) {
            if (obj instanceof Number num) {
                distinctIds.add(num.longValue());
            } else if (obj != null) {
                try {
                    distinctIds.add(Long.parseLong(obj.toString().trim()));
                } catch (NumberFormatException _) {
                    log.warn("Invalid ticket ID format in batch update: {}", obj);
                }
            }
        }
        distinctIds = distinctIds.stream().distinct().toList();

        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);
        validateProblemTarget(problemId);

        final boolean isTypeUnset = type != null && (type.trim().equalsIgnoreCase("none") || type.trim().isEmpty());
        final TicketUpdateInputType parsedType = (type != null && !isTypeUnset) ? parseTicketType(type) : null;

        if (problemId != null || (type != null && (isTypeUnset || parsedType != TicketUpdateInputType.PROBLEM))) {
            List<Ticket> currentTickets = Flux.fromIterable(distinctIds)
                    .flatMap(id -> {
                        Mono<TicketResponse> showMono = ticketClient.showTicket(id);
                        return showMono != null ? showMono.filter(resp -> resp != null && resp.getTicket() != null).map(TicketResponse::getTicket) : Mono.empty();
                    })
                    .collectList()
                    .block();
            if (currentTickets != null) {
                for (Ticket currentTicket : currentTickets) {
                    if (type != null) {
                        validateTicketTypeChange(currentTicket, parsedType, isTypeUnset);
                    }
                    if (problemId != null) {
                        TicketUpdateInput testInput = new TicketUpdateInput();
                        applyProblemIdLogic(currentTicket, testInput, problemId, convertToIncident);
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(asyncBulk)) {
            List<TicketCustomField> parsedCustomFields = parseCustomFields(customFields);
            TicketUpdateInput input = buildInputFromParams(comment, status, priority, isPublic, tokens, parsedCustomFields);
            if (requesterId != null) {
                if (requesterId > Integer.MAX_VALUE || requesterId < Integer.MIN_VALUE) {
                    throw new IllegalArgumentException("requesterId " + requesterId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits requester_id on ticket inputs to 32-bit integers.");
                }
                input.setRequesterId(requesterId.intValue());
            }
            if (type != null) {
                if (isTypeUnset) {
                    input.setType(null);
                } else {
                    input.setType(parsedType);
                }
            } else if (Boolean.TRUE.equals(convertToIncident)) {
                input.setType(TicketUpdateInputType.INCIDENT);
            }
            
            if (problemId != null) {
                input.setProblemId(problemId.intValue());
                input.setType(TicketUpdateInputType.INCIDENT);
            }

            List<lol.pbu.z4j.model.JobStatus> jobStatuses = new java.util.ArrayList<>();
            for (int i = 0; i < distinctIds.size(); i += 100) {
                List<Long> chunk = distinctIds.subList(i, Math.min(distinctIds.size(), i + 100));
                String idsStr = chunk.stream().map(Object::toString).collect(Collectors.joining(","));
                JobStatusResponse jobResponse = ticketClient.updateManyTickets(idsStr, new TicketUpdateRequest(input)).block();
                if (jobResponse != null && jobResponse.getJobStatus() != null) {
                    jobStatuses.add(jobResponse.getJobStatus());
                }
            }
            return new BatchUpdateResponse(jobStatuses.size() == 1 ? jobStatuses.get(0) : null, jobStatuses.isEmpty() ? null : jobStatuses, null);
        }

        // Concurrent immediate updates via Reactor Flux
        List<TicketUpdateResult> results = Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> {
                    List<TicketCustomField> parsedCustomFields = parseCustomFields(customFields);
                    TicketUpdateInput input = buildInputFromParams(comment, status, priority, isPublic, tokens, parsedCustomFields);
                    if (requesterId != null) {
                        if (requesterId > Integer.MAX_VALUE || requesterId < Integer.MIN_VALUE) {
                            throw new IllegalArgumentException("requesterId " + requesterId + " exceeds 32-bit integer range (max: " + Integer.MAX_VALUE + "). Upstream z4j library currently limits requester_id on ticket inputs to 32-bit integers.");
                        }
                        input.setRequesterId(requesterId.intValue());
                    }
                    if (type != null) {
                        if (isTypeUnset) {
                            input.setType(null);
                        } else {
                            input.setType(parsedType);
                        }
                    } else if (Boolean.TRUE.equals(convertToIncident)) {
                        input.setType(TicketUpdateInputType.INCIDENT);
                    }
                    
                    Mono<TicketUpdateInput> inputMono;
                    if (problemId != null) {
                        Mono<TicketResponse> showMono = ticketClient.showTicket(id);
                        if (showMono != null) {
                            inputMono = showMono.map(resp -> {
                                Ticket currentTicket = resp.getTicket();
                                applyProblemIdLogic(currentTicket, input, problemId, convertToIncident);
                                return input;
                            });
                        } else {
                            inputMono = Mono.just(input);
                        }
                    } else {
                        inputMono = Mono.just(input);
                    }
                    
                    return inputMono.flatMap(resolvedInput -> 
                                ticketClient.updateTicket(id, new TicketUpdateRequest(resolvedInput))
                            )
                            .map(resp -> new TicketUpdateResult(id, true, resp.getTicket(), null))
                            .onErrorResume(e -> {
                                log.warn("Failed to update ticket {}: {}", id, e.getMessage());
                                return Mono.just(new TicketUpdateResult(id, false, null, e.getMessage()));
                            });
                }, 10)
                .collectList()
                .block();

        return new BatchUpdateResponse(null, null, results != null ? results : Collections.emptyList());
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

    public Map<String, Object> listTicketForms() {
        return listTicketForms(null, null);
    }

    @Tool(description = "List Zendesk ticket forms. By default returns a compact summary of active forms only to save tokens. Use getTicketForm for full form details.")
    public Map<String, Object> listTicketForms(
            @ToolArg(description = "Whether to include inactive forms. Defaults to false (active forms only)") @Nullable Boolean includeInactive,
            @ToolArg(description = "Whether to return full form schemas (including conditions and field IDs) or just summary. Defaults to false (summary mode)") @Nullable Boolean fullPayload
    ) {
        log.info("MCP Tool called: listTicketForms(includeInactive={}, fullPayload={})", includeInactive, fullPayload);
        TicketFormsResponse response = ticketFormsClient.listTicketForms().block();
        if (response == null || response.getTicketForms() == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put(TICKET_FORMS_KEY, Collections.emptyList());
            return empty;
        }

        boolean activeOnly = !Boolean.TRUE.equals(includeInactive);
        boolean isFull = Boolean.TRUE.equals(fullPayload);

        List<TicketForm> forms = response.getTicketForms();
        if (activeOnly) {
            forms = forms.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getActive()))
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (isFull) {
            result.put(TICKET_FORMS_KEY, forms);
            return result;
        }

        List<Map<String, Object>> summaries = forms.stream().map(f -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", f.getId());
            s.put("name", f.getName());
            s.put("display_name", f.getDisplayName());
            s.put("active", f.getActive());
            s.put("default", f.getDefaultForm());
            return s;
        }).collect(Collectors.toList());

        result.put(TICKET_FORMS_KEY, summaries);
        return result;
    }

    @Tool(description = "Get details of a specific Zendesk ticket form by its numeric ID")
    public TicketFormResponse getTicketForm(
            @ToolArg(description = "The numeric ticket form ID") Long ticketFormId
    ) {
        log.info("MCP Tool called: getTicketForm(id={})", ticketFormId);
        return ticketFormsClient.showTicketForm(ticketFormId).block();
    }

    @Tool(description = "List all custom objects defined in Zendesk")
    public CustomObjectsResponse listCustomObjects() {
        log.info("MCP Tool called: listCustomObjects()");
        return customObjectsClient.listCustomObjects().block();
    }

    @Tool(description = "Get details and schema of a specific custom object by its key")
    public CustomObjectResponse getCustomObject(
            @ToolArg(description = "The key of the custom object") String customObjectKey
    ) {
        log.info("MCP Tool called: getCustomObject(key='{}')", customObjectKey);
        return customObjectsClient.showCustomObject(customObjectKey).block();
    }

    @Tool(description = "List records for a specific Zendesk custom object")
    public CustomObjectRecordsResponse listCustomObjectRecords(
            @ToolArg(description = "The key of the custom object") String customObjectKey
    ) {
        log.info("MCP Tool called: listCustomObjectRecords(key='{}')", customObjectKey);
        return customObjectRecordsClient.listCustomObjectRecords(customObjectKey).block();
    }

    @Tool(description = "Get details of a specific custom object record by its ID")
    public CustomObjectRecordResponse getCustomObjectRecord(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "The ID of the custom object record") String recordId
    ) {
        log.info("MCP Tool called: getCustomObjectRecord(key='{}', recordId='{}')", customObjectKey, recordId);
        return customObjectRecordsClient.showCustomObjectRecord(customObjectKey, recordId).block();
    }

    @Tool(description = "Search records for a specific Zendesk custom object matching query text")
    public CustomObjectRecordsResponse searchCustomObjectRecords(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "Search query string") String query
    ) {
        log.info("MCP Tool called: searchCustomObjectRecords(key='{}', query='{}')", customObjectKey, query);
        return customObjectRecordsClient.searchCustomObjectRecords(customObjectKey, query).block();
    }

    @Tool(description = "Get full audit event history for a ticket, including field changes, comments, notifications, and trigger/business rule executions (via via.channel='rule', via.source.rel='trigger', via.source.from.title)")
    public TicketAuditsResponse getTicketAudits(
            @ToolArg(description = "The numeric ticket ID") Long ticketId
    ) {
        log.info("MCP Tool called: getTicketAudits(id={})", ticketId);
        return ticketClient.listAuditsForTicket(ticketId).block();
    }

    @Tool(description = "List all views configured in Zendesk")
    public ViewsResponse listViews() {
        log.info("MCP Tool called: listViews()");
        return viewClient.listViews().block();
    }

    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of("articles", "sections", "categories");

    @Tool(description = "Get tickets from a specific Zendesk view by its numeric ID")
    public TicketsResponse getViewTickets(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: getViewTickets(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.listTicketsForView(viewId).block();
    }

    @Tool(description = "List only active views configured in Zendesk")
    public ViewsResponse listActiveViews() {
        log.info("MCP Tool called: listActiveViews()");
        return viewClient.listActiveViews().block();
    }

    @Tool(description = "Get details of a specific Zendesk view by its numeric ID")
    public ViewResponse getView(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: getView(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.showView(viewId).block();
    }

    @Tool(description = "Execute a specific Zendesk view by its numeric ID to retrieve ticket rows and columns")
    public ViewExecuteResponse executeView(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: executeView(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.executeView(viewId).block();
    }

    @Tool(description = "Get the ticket count for a specific Zendesk view by its numeric ID")
    public ViewCountResponse getViewTicketCount(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: getViewTicketCount(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.countView(viewId).block();
    }

    @Tool(description = "Get details and content of a specific Zendesk Help Center / Knowledge Base article by its numeric ID")
    public ArticleResponse getArticle(
            @ToolArg(description = "The numeric article ID") Long articleId,
            @ToolArg(description = "Optional locale code, e.g. 'en-us'. Defaults to 'en-us'") @Nullable String locale
    ) {
        log.info("MCP Tool called: getArticle(id={}, locale='{}')", articleId, locale);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        return articleClient.showArticle(localeAbbr, articleId).block();
    }

    @Tool(description = "List Zendesk Help Center / Knowledge Base articles")
    public ArticlesResponse listArticles(
            @ToolArg(description = "Optional locale code, e.g. 'en-us'. Defaults to 'en-us'") @Nullable String locale,
            @ToolArg(description = "Optional sort field: position, title, created_at, updated_at, edited_at") @Nullable String sortBy,
            @ToolArg(description = "Optional sort order: asc or desc") @Nullable String sortOrder,
            @ToolArg(description = "Optional start time Unix epoch timestamp for incremental listing") @Nullable Long startTime,
            @ToolArg(description = "Optional comma-delimited label names") @Nullable String labelNames
    ) {
        log.info("MCP Tool called: listArticles(locale='{}', sortBy='{}')", locale, sortBy);
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        return articleClient.listArticles(
                localeAbbr,
                resolveSortArticleBy(sortBy),
                resolveSortOrder(sortOrder),
                startTime,
                labelNames
        ).block();
    }

    @Tool(description = "Create a new article in a Zendesk Help Center section")
    public ArticleResponse createArticle(
            @ToolArg(description = "The ID of the section to which the article belongs") Long sectionId,
            @ToolArg(description = "The title of the article") String title,
            @ToolArg(description = "The HTML body content of the article") String body,
            @ToolArg(description = "The ID of the permission group defining who can edit/publish this article") Long permissionGroupId,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale,
            @ToolArg(description = "Optional flag indicating whether this article is a draft. Defaults to true for safety") @Nullable Boolean draft,
            @ToolArg(description = "Optional list of label names for the article") @Nullable List<String> labelNames,
            @ToolArg(description = "Optional user segment ID defining who can view this article") @Nullable Long userSegmentId
    ) {
        log.info("MCP Tool called: createArticle(sectionId={}, title='{}')", sectionId, title);
        if (sectionId == null) {
            throw new IllegalArgumentException("sectionId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        if (permissionGroupId == null) {
            throw new IllegalArgumentException("permissionGroupId is required");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        boolean isDraft = draft == null || draft;
        Article article = new Article()
                .setTitle(title)
                .setBody(body)
                .setPermissionGroupId(permissionGroupId)
                .setLocaleAbbreviation(localeAbbr)
                .setDraft(isDraft);
        if (labelNames != null && !labelNames.isEmpty()) {
            article.setLabelNames(labelNames);
        }
        if (userSegmentId != null) {
            article.setUserSegmentId(userSegmentId);
        }
        return articleClient.createArticle(localeAbbr, sectionId, new ArticleCreateRequest(article)).block();
    }

    @Tool(description = "Update an existing Zendesk Help Center article")
    public ArticleResponse updateArticle(
            @ToolArg(description = "The unique numeric ID of the article") Long articleId,
            @ToolArg(description = "Optional new title of the article") @Nullable String title,
            @ToolArg(description = "Optional new HTML body content of the article") @Nullable String body,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale,
            @ToolArg(description = "Optional flag to publish (false) or draft (true) the article") @Nullable Boolean draft,
            @ToolArg(description = "Optional permission group ID defining who can edit/publish this article") @Nullable Long permissionGroupId,
            @ToolArg(description = "Optional list of label names for the article") @Nullable List<String> labelNames,
            @ToolArg(description = "Optional user segment ID defining who can view this article") @Nullable Long userSegmentId
    ) {
        log.info("MCP Tool called: updateArticle(articleId={})", articleId);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        if (title == null && body == null && labelNames == null && userSegmentId == null && draft == null && permissionGroupId == null) {
            throw new IllegalArgumentException("At least one field to update (title, body, labelNames, userSegmentId, draft, permissionGroupId) must be provided.");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        Article article = new Article();
        if (title != null) {
            article.setTitle(title);
        }
        if (body != null) {
            article.setBody(body);
        }
        if (draft != null) {
            article.setDraft(draft);
        }
        if (permissionGroupId != null) {
            article.setPermissionGroupId(permissionGroupId);
        }
        if (labelNames != null) {
            article.setLabelNames(labelNames);
        }
        if (userSegmentId != null) {
            article.setUserSegmentId(userSegmentId);
        }
        return articleClient.updateArticle(localeAbbr, articleId, new ArticleUpdateRequest(article)).block();
    }

    @Tool(description = "Delete a Zendesk Help Center article translation for a given locale (or the article if it is the only translation). Requires confirm=true to prevent accidental deletion")
    public Map<String, Object> deleteArticle(
            @ToolArg(description = "The unique numeric ID of the article to delete") Long articleId,
            @ToolArg(description = "Must be explicitly set to true to confirm deletion of this article") @Nullable Boolean confirm,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale
    ) {
        log.info("MCP Tool called: deleteArticle(articleId={}, confirm={})", articleId, confirm);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        if (!Boolean.TRUE.equals(confirm)) {
            throw new IllegalArgumentException("Deletion requires explicit confirmation. Set 'confirm' to true to proceed.");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        articleClient.deleteArticle(localeAbbr, articleId).block();
        return Map.of("success", true, "deletedArticleId", articleId);
    }

    @Tool(description = "List translations for a Zendesk Help Center resource (e.g. 'articles', 'sections', 'categories')")
    public TranslationsResponse listTranslations(
            @ToolArg(description = "The resource type: 'articles', 'sections', or 'categories'") String resourceType,
            @ToolArg(description = "The numeric ID of the parent resource") Long resourceId
    ) {
        log.info("MCP Tool called: listTranslations(resourceType='{}', resourceId={})", resourceType, resourceId);
        String validResourceType = validateResourceType(resourceType);
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId is required");
        }
        return translationClient.listTranslations(validResourceType, resourceId).block();
    }

    @Tool(description = "Get a specific translation for a Zendesk Help Center resource by locale")
    public TranslationResponse getTranslation(
            @ToolArg(description = "The resource type: 'articles', 'sections', or 'categories'") String resourceType,
            @ToolArg(description = "The numeric ID of the parent resource") Long resourceId,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale
    ) {
        log.info("MCP Tool called: getTranslation(resourceType='{}', resourceId={}, locale='{}')", resourceType, resourceId, locale);
        String validResourceType = validateResourceType(resourceType);
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId is required");
        }
        return translationClient.showTranslation(validResourceType, resourceId, resolveLocale(locale)).block();
    }

    @Tool(description = "List Zendesk Help Center categories")
    public CategoriesResponse listCategories(
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). If omitted, lists categories across all locales") @Nullable String locale
    ) {
        log.info("MCP Tool called: listCategories(locale='{}')", locale);
        if (locale != null && !locale.isBlank()) {
            return categoryClient.listCategories(resolveLocale(locale), null, null).block();
        }
        return categoryClient.listCategoriesNoLocale(null, null).block();
    }

    @Tool(description = "Get details of a specific Zendesk Help Center category by its numeric ID")
    public CategoryResponse getCategory(
            @ToolArg(description = "The numeric category ID") Long categoryId,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us')") @Nullable String locale
    ) {
        log.info("MCP Tool called: getCategory(categoryId={}, locale='{}')", categoryId, locale);
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId is required");
        }
        if (locale != null && !locale.isBlank()) {
            return categoryClient.showCategory(resolveLocale(locale), categoryId).block();
        }
        return categoryClient.showCategoryNoLocale(categoryId).block();
    }

    @Tool(description = "List all Zendesk Community topics")
    public TopicsResponse listCommunityTopics() {
        log.info("MCP Tool called: listCommunityTopics()");
        return topicClient.listTopics().block();
    }

    @Tool(description = "Get details of a specific Zendesk Community topic by its numeric ID")
    public TopicResponse getCommunityTopic(
            @ToolArg(description = "The numeric topic ID") Long topicId
    ) {
        log.info("MCP Tool called: getCommunityTopic(topicId={})", topicId);
        if (topicId == null) {
            throw new IllegalArgumentException("topicId is required");
        }
        return topicClient.showTopic(topicId).block();
    }

    @Tool(description = "List Zendesk Community posts, optionally filtered by topic ID")
    public PostsResponse listCommunityPosts(
            @ToolArg(description = "Optional topic ID to filter posts by") @Nullable Long topicId
    ) {
        log.info("MCP Tool called: listCommunityPosts(topicId={})", topicId);
        if (topicId != null) {
            return postClient.listPostsByTopic(topicId).block();
        }
        return postClient.listPosts().block();
    }

    @Tool(description = "Get details of a specific Zendesk Community post by its numeric ID")
    public PostResponse getCommunityPost(
            @ToolArg(description = "The numeric post ID") Long postId
    ) {
        log.info("MCP Tool called: getCommunityPost(postId={})", postId);
        if (postId == null) {
            throw new IllegalArgumentException("postId is required");
        }
        return postClient.showPost(postId).block();
    }

    @Tool(description = "Search Zendesk Community posts matching a query string")
    public CommunityPostSearchResponse searchCommunityPosts(
            @ToolArg(description = "The search query string") String query
    ) {
        log.info("MCP Tool called: searchCommunityPosts(query='{}')", query);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query cannot be null or blank");
        }
        return postClient.searchPosts(query).block();
    }

    @Tool(description = "List comments for a specific Zendesk Community post by its numeric ID")
    public PostCommentsResponse listCommunityPostComments(
            @ToolArg(description = "The numeric post ID") Long postId
    ) {
        log.info("MCP Tool called: listCommunityPostComments(postId={})", postId);
        if (postId == null) {
            throw new IllegalArgumentException("postId is required");
        }
        return postClient.listPostComments(postId).block();
    }

    private String validateResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType is required. Allowed values are: " + ALLOWED_RESOURCE_TYPES);
        }
        String normalized = resourceType.trim().toLowerCase();
        if ("article".equals(normalized)) {
            normalized = "articles";
        } else if ("section".equals(normalized)) {
            normalized = "sections";
        } else if ("category".equals(normalized)) {
            normalized = "categories";
        }
        if (!ALLOWED_RESOURCE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid resourceType: '" + resourceType + "'. Allowed values are: " + ALLOWED_RESOURCE_TYPES);
        }
        return normalized;
    }

    private LocaleAbbreviation resolveLocale(@Nullable String locale) {
        if (locale == null || locale.isBlank()) {
            return LocaleAbbreviation.ENGLISH_UNITED_STATES;
        }
        String cleanLocale = locale.trim().toLowerCase();
        if ("no".equals(cleanLocale)) {
            return LocaleAbbreviation.NORWEGIAN;
        }
        try {
            return LocaleAbbreviation.fromValue(cleanLocale);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid locale: '" + locale + "'. Expected a standard locale code such as 'en-us', 'es', 'fr', 'de', 'ja', etc.");
        }
    }

    private SortArticleBy resolveSortArticleBy(@Nullable String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }
        String cleanSort = sortBy.trim().toLowerCase();
        try {
            return SortArticleBy.fromValue(cleanSort);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid sortBy: '" + sortBy + "'. Allowed values are: position, title, created_at, updated_at, edited_at");
        }
    }

    private SortOrder resolveSortOrder(@Nullable String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) {
            return null;
        }
        String cleanOrder = sortOrder.trim().toLowerCase();
        if ("ascending".equals(cleanOrder)) {
            cleanOrder = "asc";
        } else if ("descending".equals(cleanOrder)) {
            cleanOrder = "desc";
        }
        try {
            return SortOrder.fromValue(cleanOrder);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid sortOrder: '" + sortOrder + "'. Allowed values are: 'asc' or 'desc'");
        }
    }

}
