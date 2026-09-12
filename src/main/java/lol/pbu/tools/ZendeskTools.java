package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Singleton;
import lol.pbu.model.BatchUpdateResponse;
import lol.pbu.model.BatchUpdateResponse.TicketUpdateResult;
import lol.pbu.z4j.client.AttachmentClient;
import lol.pbu.z4j.client.CustomObjectRecordsClient;
import lol.pbu.z4j.client.CustomObjectsClient;
import lol.pbu.z4j.client.JobStatusClient;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.client.TicketClient;
import lol.pbu.z4j.client.TicketFormsClient;
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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MCP Tools exposing Zendesk ticket management, forms, custom objects, attachments, and search operations via z4j.
 */
@Singleton
public class ZendeskTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTools.class);

    private final TicketClient ticketClient;
    private final SearchClient searchClient;
    private final TicketFormsClient ticketFormsClient;
    private final CustomObjectsClient customObjectsClient;
    private final CustomObjectRecordsClient customObjectRecordsClient;
    private final AttachmentClient attachmentClient;
    private final JobStatusClient jobStatusClient;

    public ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient
    ) {
        this.ticketClient = ticketClient;
        this.searchClient = searchClient;
        this.ticketFormsClient = ticketFormsClient;
        this.customObjectsClient = customObjectsClient;
        this.customObjectRecordsClient = customObjectRecordsClient;
        this.attachmentClient = attachmentClient;
        this.jobStatusClient = jobStatusClient;
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
                } catch (NumberFormatException e) {
                    log.warn("Invalid ticket ID format: {}", obj);
                }
            }
        }
        distinctIds = distinctIds.stream().distinct().toList();

        List<Ticket> tickets = Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> ticketClient.showTicket(id)
                        .map(TicketResponse::getTicket)
                        .onErrorResume(e -> {
                            log.warn("Failed to fetch ticket {}: {}", id, e.getMessage());
                            return Mono.empty();
                        }), 10)
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
            @ToolArg(description = "Page number (1-based, default 1)") @Nullable Integer page,
            @ToolArg(description = "Number of results per page (default 25, max 100)") @Nullable Integer perPage
    ) {
        int p = (page != null && page > 0) ? page : 1;
        int size = (perPage != null && perPage > 0) ? Math.min(perPage, 100) : 25;
        String resolvedInclude = null;
        if (StringUtils.isNotEmpty(include)) {
            String trimmed = include.trim();
            if (!trimmed.contains("(")) {
                resolvedInclude = "tickets(" + trimmed + ")";
            } else {
                resolvedInclude = trimmed;
            }
        }
        log.info("MCP Tool called: search(query='{}', include='{}', page={}, perPage={})", query, resolvedInclude, p, size);
        return searchClient.list(query, resolvedInclude, null, null, p, size).block();
    }

    @Tool(description = "Get the count of search results matching a query in Zendesk")
    public SearchResponse searchCount(
            @ToolArg(description = "Zendesk search query string, e.g. 'type:ticket status:open'") String query
    ) {
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
            @ToolArg(description = "The initial comment / description of the ticket") String comment,
            @ToolArg(description = "Priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths
    ) {
        log.info("MCP Tool called: createTicket(subject='{}')", subject);
        TicketComment ticketComment = new TicketComment().setBody(comment);
        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);
        if (!tokens.isEmpty()) {
            ticketComment.setUploads(tokens);
        }
        TicketCreateInput input = new TicketCreateInput(ticketComment);
        input.setRawSubject(subject);

        if (StringUtils.isNotEmpty(priority)) {
            try {
                input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
            } catch (Exception e) {
                log.warn("Unknown priority '{}', ignoring", priority);
            }
        }
        if (StringUtils.isNotEmpty(status)) {
            try {
                input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
            } catch (Exception e) {
                log.warn("Unknown status '{}', ignoring", status);
            }
        }

        return ticketClient.createTicket(new TicketCreateRequest(input)).block();
    }

    @Tool(description = "Update an existing Zendesk ticket with a comment, status, priority, or attachments")
    public TicketUpdateResponse updateTicket(
            @ToolArg(description = "The numeric ticket ID to update") Long ticketId,
            @ToolArg(description = "Comment text to add to the ticket") @Nullable String comment,
            @ToolArg(description = "New status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "New priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Whether the comment is public (true) or private internal note (false)") @Nullable Boolean isPublic,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths
    ) {
        log.info("MCP Tool called: updateTicket(id={})", ticketId);
        TicketUpdateInput input = new TicketUpdateInput();
        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);

        if (StringUtils.isNotEmpty(comment) || !tokens.isEmpty()) {
            TicketComment ticketComment = new TicketComment();
            if (StringUtils.isNotEmpty(comment)) {
                ticketComment.setBody(comment);
            }
            if (isPublic != null) {
                ticketComment.setIsPublic(isPublic);
            }
            if (!tokens.isEmpty()) {
                ticketComment.setUploads(tokens);
            }
            input.setComment(ticketComment);
        }
        if (StringUtils.isNotEmpty(status)) {
            try {
                input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
            } catch (Exception e) {
                log.warn("Unknown status '{}', ignoring", status);
            }
        }
        if (StringUtils.isNotEmpty(priority)) {
            try {
                input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
            } catch (Exception e) {
                log.warn("Unknown priority '{}', ignoring", priority);
            }
        }

        return ticketClient.updateTicket(ticketId, new TicketUpdateRequest(input)).block();
    }

    @Tool(description = "Upload a local file or image as an attachment to Zendesk, returning an upload token to attach to tickets")
    public AttachmentUploadResponse uploadAttachment(
            @ToolArg(description = "Path to the local file to upload") String filePath,
            @ToolArg(description = "Optional custom filename to use in Zendesk (defaults to base name of file)") @Nullable String filename
    ) {
        log.info("MCP Tool called: uploadAttachment(filePath='{}', filename='{}')", filePath, filename);
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

    @Tool(description = "Batch update multiple Zendesk tickets by their numeric IDs with a comment, status, priority, or attachments. Supports concurrent immediate updates or Zendesk async bulk jobs.")
    public BatchUpdateResponse batchUpdateTickets(
            @ToolArg(description = "List of numeric ticket IDs to update") List<Long> ticketIds,
            @ToolArg(description = "Comment text to add to the tickets") @Nullable String comment,
            @ToolArg(description = "New status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "New priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Whether the comment is public (true) or private internal note (false)") @Nullable Boolean isPublic,
            @ToolArg(description = "Optional upload tokens obtained from uploadAttachment") @Nullable List<String> uploadTokens,
            @ToolArg(description = "Optional local file paths to upload and attach automatically") @Nullable List<String> attachmentFilePaths,
            @ToolArg(description = "If true, queues an async bulk job in Zendesk (PUT /api/v2/tickets/update_many) returning JobStatus. If false (default), updates tickets concurrently via Reactor returning immediate per-ticket results.") @Nullable Boolean asyncBulk
    ) {
        log.info("MCP Tool called: batchUpdateTickets(ids={}, asyncBulk={})", ticketIds, asyncBulk);
        if (ticketIds == null || ticketIds.isEmpty()) {
            return new BatchUpdateResponse(null, Collections.emptyList());
        }

        List<?> rawIds = ticketIds;
        List<Long> distinctIds = new ArrayList<>();
        for (Object obj : rawIds) {
            if (obj instanceof Number num) {
                distinctIds.add(num.longValue());
            } else if (obj != null) {
                try {
                    distinctIds.add(Long.parseLong(obj.toString().trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid ticket ID format in batch update: {}", obj);
                }
            }
        }
        distinctIds = distinctIds.stream().distinct().toList();

        List<String> tokens = resolveUploadTokens(uploadTokens, attachmentFilePaths);

        if (Boolean.TRUE.equals(asyncBulk)) {
            TicketUpdateInput input = new TicketUpdateInput();
            if (StringUtils.isNotEmpty(comment) || !tokens.isEmpty()) {
                TicketComment ticketComment = new TicketComment();
                if (StringUtils.isNotEmpty(comment)) {
                    ticketComment.setBody(comment);
                }
                if (isPublic != null) {
                    ticketComment.setIsPublic(isPublic);
                }
                if (!tokens.isEmpty()) {
                    ticketComment.setUploads(tokens);
                }
                input.setComment(ticketComment);
            }
            if (StringUtils.isNotEmpty(status)) {
                try {
                    input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
                } catch (Exception e) {
                    log.warn("Unknown status '{}', ignoring", status);
                }
            }
            if (StringUtils.isNotEmpty(priority)) {
                try {
                    input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
                } catch (Exception e) {
                    log.warn("Unknown priority '{}', ignoring", priority);
                }
            }

            String idsStr = distinctIds.stream().map(Object::toString).collect(Collectors.joining(","));
            JobStatusResponse jobResponse = ticketClient.updateManyTickets(idsStr, new TicketUpdateRequest(input)).block();
            return new BatchUpdateResponse(jobResponse != null ? jobResponse.getJobStatus() : null, null);
        }

        // Concurrent immediate updates via Reactor Flux
        List<TicketUpdateResult> results = Flux.fromIterable(distinctIds)
                .flatMapSequential(id -> {
                    TicketUpdateInput input = new TicketUpdateInput();
                    if (StringUtils.isNotEmpty(comment) || !tokens.isEmpty()) {
                        TicketComment ticketComment = new TicketComment();
                        if (StringUtils.isNotEmpty(comment)) {
                            ticketComment.setBody(comment);
                        }
                        if (isPublic != null) {
                            ticketComment.setIsPublic(isPublic);
                        }
                        if (!tokens.isEmpty()) {
                            ticketComment.setUploads(tokens);
                        }
                        input.setComment(ticketComment);
                    }
                    if (StringUtils.isNotEmpty(status)) {
                        try {
                            input.setStatus(TicketUpdateInputStatus.fromValue(status.toLowerCase().trim()));
                        } catch (Exception e) {
                            log.warn("Unknown status '{}', ignoring", status);
                        }
                    }
                    if (StringUtils.isNotEmpty(priority)) {
                        try {
                            input.setPriority(TicketUpdateInputPriority.fromValue(priority.toLowerCase().trim()));
                        } catch (Exception e) {
                            log.warn("Unknown priority '{}', ignoring", priority);
                        }
                    }

                    return ticketClient.updateTicket(id, new TicketUpdateRequest(input))
                            .map(resp -> new TicketUpdateResult(id, true, resp.getTicket(), null))
                            .onErrorResume(e -> {
                                log.warn("Failed to update ticket {}: {}", id, e.getMessage());
                                return Mono.just(new TicketUpdateResult(id, false, null, e.getMessage()));
                            });
                }, 10)
                .collectList()
                .block();

        return new BatchUpdateResponse(null, results != null ? results : Collections.emptyList());
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

    @Tool(description = "List all ticket forms configured in Zendesk")
    public TicketFormsResponse listTicketForms() {
        log.info("MCP Tool called: listTicketForms()");
        return ticketFormsClient.listTicketForms().block();
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

}
