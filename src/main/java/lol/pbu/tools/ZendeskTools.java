package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.client.TicketClient;
import lol.pbu.z4j.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * MCP Tools exposing Zendesk ticket management and search operations via z4j.
 */
@Singleton
public class ZendeskTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTools.class);

    private final TicketClient ticketClient;
    private final SearchClient searchClient;

    public ZendeskTools(TicketClient ticketClient, SearchClient searchClient) {
        this.ticketClient = ticketClient;
        this.searchClient = searchClient;
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
}
