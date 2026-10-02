package lol.pbu.tools;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.ViewClient;
import lol.pbu.z4j.model.TicketsResponse;
import lol.pbu.z4j.model.ViewCountResponse;
import lol.pbu.z4j.model.ViewExecuteResponse;
import lol.pbu.z4j.model.ViewResponse;
import lol.pbu.z4j.model.ViewsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Singleton
public class ZendeskViewTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskViewTools.class);
    private final ViewClient viewClient;

    @Inject
    public ZendeskViewTools(ViewClient viewClient) {
        this.viewClient = viewClient;
    }

    @Tool(description = "List all views configured in Zendesk")
    public Mono<ViewsResponse> listViews() {
        log.info("MCP Tool called: listViews()");
        return viewClient.listViews();
    }

    @Tool(description = "Get tickets from a specific Zendesk view by its numeric ID")
    public Mono<TicketsResponse> getViewTickets(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: getViewTickets(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.listTicketsForView(viewId);
    }

    @Tool(description = "List only active views configured in Zendesk")
    public Mono<ViewsResponse> listActiveViews() {
        log.info("MCP Tool called: listActiveViews()");
        return viewClient.listActiveViews();
    }

    @Tool(description = "Get details of a specific Zendesk view by its numeric ID")
    public Mono<ViewResponse> getView(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: getView(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.showView(viewId);
    }

    @Tool(description = "Execute a specific Zendesk view by its numeric ID to retrieve ticket rows and columns")
    public Mono<ViewExecuteResponse> executeView(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: executeView(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.executeView(viewId);
    }

    @Tool(description = "Get the ticket count for a specific Zendesk view by its numeric ID")
    public Mono<ViewCountResponse> getViewTicketCount(
            @ToolArg(description = "The numeric view ID") Long viewId
    ) {
        log.info("MCP Tool called: getViewTicketCount(viewId={})", viewId);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        return viewClient.countView(viewId);
    }
}
