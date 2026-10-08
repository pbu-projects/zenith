package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.model.LeanTicket;
import lol.pbu.model.ViewTicketsResult;
import lol.pbu.z4j.client.ViewClient;
import lol.pbu.z4j.model.Meta;
import lol.pbu.z4j.model.Ticket;
import lol.pbu.z4j.model.TicketsResponse;
import lol.pbu.z4j.model.ViewCountResponse;
import lol.pbu.z4j.model.ViewExecuteResponse;
import lol.pbu.z4j.model.ViewResponse;
import lol.pbu.z4j.model.ViewsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class ZendeskViewTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskViewTools.class);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_RESULTS = 500;

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

    @Tool(description = "Get tickets from a specific Zendesk view by its numeric ID with pagination and lean payload options")
    public Mono<ViewTicketsResult> getViewTickets(
            @ToolArg(description = "The numeric view ID") Long viewId,
            @ToolArg(description = "Cursor token for fetching the next page of results") @Nullable String cursor,
            @ToolArg(description = "Number of tickets to return per page (1 to 100, default 100)") @Nullable Integer pageSize,
            @ToolArg(description = "Whether to auto-page and aggregate tickets up to maxResults (default false)") @Nullable Boolean autoPage,
            @ToolArg(description = "Maximum number of tickets to retrieve when auto-paging (default 500)") @Nullable Integer maxResults,
            @ToolArg(description = "Whether to include non-null custom fields (default false)") @Nullable Boolean includeCustomFields
    ) {
        log.info("MCP Tool called: getViewTickets(viewId={}, cursor={}, pageSize={}, autoPage={}, maxResults={}, includeCustomFields={})",
                viewId, cursor, pageSize, autoPage, maxResults, includeCustomFields);
        if (viewId == null) {
            throw new IllegalArgumentException("viewId is required");
        }
        int effectivePageSize = resolvePageSize(pageSize);
        int effectiveMaxResults = resolveMaxResults(maxResults);
        String effectiveCursor = (cursor != null && !cursor.isBlank()) ? cursor : null;
        boolean includeCustom = Boolean.TRUE.equals(includeCustomFields);

        if (Boolean.TRUE.equals(autoPage)) {
            return getAutoPagedViewTickets(viewId, effectiveCursor, effectivePageSize, effectiveMaxResults, includeCustom);
        }

        return getSinglePageViewTickets(viewId, effectiveCursor, effectivePageSize, includeCustom);
    }

    public Mono<ViewTicketsResult> getViewTickets(Long viewId) {
        return getViewTickets(viewId, null, null, null, null, null);
    }

    public Mono<ViewTicketsResult> getViewTickets(Long viewId, @Nullable String cursor) {
        return getViewTickets(viewId, cursor, null, null, null, null);
    }

    public Mono<ViewTicketsResult> getViewTickets(Long viewId, @Nullable String cursor, @Nullable Integer pageSize) {
        return getViewTickets(viewId, cursor, pageSize, null, null, null);
    }

    public Mono<ViewTicketsResult> getViewTickets(
            Long viewId,
            @Nullable String cursor,
            @Nullable Integer pageSize,
            @Nullable Boolean autoPage,
            @Nullable Boolean includeCustomFields
    ) {
        return getViewTickets(viewId, cursor, pageSize, autoPage, null, includeCustomFields);
    }

    private Mono<ViewTicketsResult> getSinglePageViewTickets(
            Long viewId,
            @Nullable String cursor,
            int pageSize,
            boolean includeCustom
    ) {
        Mono<TicketsResponse> ticketsMono = viewClient.listTicketsForView(viewId, cursor, pageSize);
        Mono<Optional<Long>> countMono = (cursor == null)
                ? fetchTotalCount(viewId)
                : Mono.just(Optional.empty());

        return Mono.zip(ticketsMono, countMono)
                .map(tuple -> {
                    TicketsResponse resp = tuple.getT1();
                    Long sideloadedCount = tuple.getT2().orElse(null);
                    return toViewTicketsResult(resp, sideloadedCount, includeCustom);
                })
                .defaultIfEmpty(new ViewTicketsResult(Collections.emptyList(), 0, null, false, null));
    }

    private Mono<ViewTicketsResult> getAutoPagedViewTickets(
            Long viewId,
            @Nullable String cursor,
            int pageSize,
            int maxResults,
            boolean includeCustom
    ) {
        Mono<LeanPage> initialPageMono = viewClient.listTicketsForView(viewId, cursor, pageSize)
                .map(resp -> toLeanPage(resp, includeCustom));
        Mono<Optional<Long>> countMono = (cursor == null)
                ? fetchTotalCount(viewId)
                : Mono.just(Optional.empty());

        return Mono.zip(initialPageMono, countMono)
                .flatMap(tuple -> {
                    LeanPage firstPage = tuple.getT1();
                    Long sideloadedCount = tuple.getT2().orElse(null);
                    Long totalCount = firstPage.totalCount() != null ? firstPage.totalCount() : sideloadedCount;

                    PagingState initial = new PagingState(firstPage, firstPage.tickets().size());

                    return Mono.just(initial)
                            .expand(state -> {
                                if (state.accumulatedCount >= maxResults) {
                                    return Mono.empty();
                                }
                                if (!state.page.hasMore() || state.page.nextCursor() == null || state.page.nextCursor().isBlank()) {
                                    return Mono.empty();
                                }
                                return viewClient.listTicketsForView(viewId, state.page.nextCursor(), pageSize)
                                        .map(nextResp -> {
                                            LeanPage nextPage = toLeanPage(nextResp, includeCustom);
                                            return new PagingState(nextPage, state.accumulatedCount + nextPage.tickets().size());
                                        });
                            })
                            .collectList()
                            .map(pages -> aggregatePagedResults(pages, totalCount, maxResults));
                })
                .defaultIfEmpty(new ViewTicketsResult(Collections.emptyList(), 0, null, false, null));
    }

    private Mono<Optional<Long>> fetchTotalCount(Long viewId) {
        try {
            Mono<ViewCountResponse> countMono = viewClient.countView(viewId);
            if (countMono == null) {
                return Mono.just(Optional.empty());
            }
            return countMono
                    .map(r -> {
                        if (r != null && r.getViewCount() != null && r.getViewCount().getValue() != null) {
                            return Optional.of(r.getViewCount().getValue());
                        }
                        return Optional.<Long>empty();
                    })
                    .defaultIfEmpty(Optional.empty())
                    .onErrorResume(e -> Mono.just(Optional.empty()));
        } catch (Exception e) {
            return Mono.just(Optional.empty());
        }
    }

    private LeanPage toLeanPage(TicketsResponse resp, boolean includeCustom) {
        if (resp == null) {
            return new LeanPage(Collections.emptyList(), null, false, null);
        }
        List<Ticket> rawTickets = resp.getTickets() != null ? resp.getTickets() : Collections.emptyList();
        List<LeanTicket> leanTickets = rawTickets.stream()
                .filter(Objects::nonNull)
                .map(ticket -> LeanTicket.fromTicket(ticket, includeCustom))
                .toList();

        Long totalCount = resp.getCount() != null ? Long.valueOf(resp.getCount()) : null;
        boolean hasMore = false;
        String nextCursor = null;

        Meta meta = resp.getMeta();
        if (meta != null) {
            hasMore = Boolean.TRUE.equals(meta.getHasMore());
            nextCursor = meta.getAfterCursor();
        }

        return new LeanPage(leanTickets, nextCursor, hasMore, totalCount);
    }

    private ViewTicketsResult toViewTicketsResult(
            TicketsResponse resp,
            @Nullable Long fallbackCount,
            boolean includeCustom
    ) {
        if (resp == null) {
            return new ViewTicketsResult(Collections.emptyList(), 0, fallbackCount, false, null);
        }
        LeanPage page = toLeanPage(resp, includeCustom);
        Long effectiveTotalCount = page.totalCount() != null ? page.totalCount() : fallbackCount;

        return new ViewTicketsResult(
                page.tickets(),
                page.tickets().size(),
                effectiveTotalCount,
                page.hasMore(),
                page.nextCursor()
        );
    }

    private ViewTicketsResult aggregatePagedResults(
            List<PagingState> states,
            @Nullable Long totalCount,
            int maxResults
    ) {
        if (states == null || states.isEmpty()) {
            return new ViewTicketsResult(Collections.emptyList(), 0, totalCount, false, null);
        }
        List<LeanTicket> allTickets = new ArrayList<>();
        Long resolvedTotalCount = totalCount;

        for (PagingState state : states) {
            if (resolvedTotalCount == null && state.page.totalCount() != null) {
                resolvedTotalCount = state.page.totalCount();
            }
            allTickets.addAll(state.page.tickets());
        }

        PagingState lastState = states.get(states.size() - 1);
        boolean hasMore = lastState.page.hasMore();
        String nextCursor = lastState.page.nextCursor();

        if (allTickets.size() >= maxResults && lastState.page.hasMore()) {
            hasMore = true;
            nextCursor = lastState.page.nextCursor();
        }

        return new ViewTicketsResult(
                Collections.unmodifiableList(allTickets),
                allTickets.size(),
                resolvedTotalCount,
                hasMore,
                nextCursor
        );
    }

    private static int resolvePageSize(@Nullable Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static int resolveMaxResults(@Nullable Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return DEFAULT_MAX_RESULTS;
        }
        return maxResults;
    }

    private record LeanPage(
            List<LeanTicket> tickets,
            @Nullable String nextCursor,
            boolean hasMore,
            @Nullable Long totalCount
    ) {}

    private record PagingState(
            LeanPage page,
            int accumulatedCount
    ) {}

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
