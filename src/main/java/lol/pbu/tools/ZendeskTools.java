package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.CustomObjectRecordsClient;
import lol.pbu.z4j.client.CustomObjectsClient;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.client.TicketClient;
import lol.pbu.z4j.client.TicketFormsClient;
import lol.pbu.z4j.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * MCP Tools exposing Zendesk ticket management, forms, custom objects, and search operations via z4j.
 */
@Singleton
public class ZendeskTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTools.class);

    private final TicketClient ticketClient;
    private final SearchClient searchClient;
    private final TicketFormsClient ticketFormsClient;
    private final CustomObjectsClient customObjectsClient;
    private final CustomObjectRecordsClient customObjectRecordsClient;

    public ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient
    ) {
        this.ticketClient = ticketClient;
        this.searchClient = searchClient;
        this.ticketFormsClient = ticketFormsClient;
        this.customObjectsClient = customObjectsClient;
        this.customObjectRecordsClient = customObjectRecordsClient;
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

    @Tool(description = "Search Zendesk using Zendesk search syntax (e.g. 'type:ticket status:open', 'type:ticket created>2026-01-01')")
    public SearchResponse search(
            @ToolArg(description = "Zendesk search query string") String query,
            @ToolArg(description = "Page number (1-based, default 1)") @Nullable Integer page,
            @ToolArg(description = "Number of results per page (default 25, max 100)") @Nullable Integer perPage
    ) {
        int p = (page != null && page > 0) ? page : 1;
        int size = (perPage != null && perPage > 0) ? Math.min(perPage, 100) : 25;
        log.info("MCP Tool called: search(query='{}', page={}, perPage={})", query, p, size);
        return searchClient.list(query, null, null, p, size).block();
    }

    @Tool(description = "Get the count of search results matching a query in Zendesk")
    public SearchResponse searchCount(
            @ToolArg(description = "Zendesk search query string, e.g. 'type:ticket status:open'") String query
    ) {
        log.info("MCP Tool called: searchCount(query='{}')", query);
        return searchClient.count(query).block();
    }

    @Tool(description = "Create a new Zendesk ticket")
    public TicketResponse createTicket(
            @ToolArg(description = "The subject of the ticket") String subject,
            @ToolArg(description = "The initial comment / description of the ticket") String comment,
            @ToolArg(description = "Priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Status: new, open, pending, hold, solved, closed") @Nullable String status
    ) {
        log.info("MCP Tool called: createTicket(subject='{}')", subject);
        TicketComment ticketComment = new TicketComment().setBody(comment);
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

    @Tool(description = "Update an existing Zendesk ticket with a comment, status, or priority")
    public TicketUpdateResponse updateTicket(
            @ToolArg(description = "The numeric ticket ID to update") Long ticketId,
            @ToolArg(description = "Comment text to add to the ticket") @Nullable String comment,
            @ToolArg(description = "New status: new, open, pending, hold, solved, closed") @Nullable String status,
            @ToolArg(description = "New priority: urgent, high, normal, low") @Nullable String priority,
            @ToolArg(description = "Whether the comment is public (true) or private internal note (false)") @Nullable Boolean isPublic
    ) {
        log.info("MCP Tool called: updateTicket(id={})", ticketId);
        TicketUpdateInput input = new TicketUpdateInput();

        if (StringUtils.isNotEmpty(comment)) {
            TicketComment ticketComment = new TicketComment().setBody(comment);
            if (isPublic != null) {
                ticketComment.setIsPublic(isPublic);
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
}
