package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.model.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@Singleton
public class ZendeskSearchTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskSearchTools.class);
    private static final String PROBLEM_ID_FILTER = "problem_id:";
    private final SearchClient searchClient;

    @Inject
    public ZendeskSearchTools(SearchClient searchClient) {
        this.searchClient = searchClient;
    }

    private void validateSearchQuery(String query) {
        if (query != null && query.toLowerCase().contains(PROBLEM_ID_FILTER)) {
            throw new IllegalArgumentException("Zendesk search does not support filtering by problem_id. The search index does not cover this field, and queries will silently return 0 results. If you need to find incidents linked to a problem, you must retrieve tickets individually or use a different discovery mechanism.");
        }
    }

    private String resolveInclude(@Nullable String include) {
        if (include == null || include.isBlank()) {
            return null;
        }
        String trimmed = include.trim();
        return trimmed.contains("(") ? trimmed : "tickets(" + trimmed + ")";
    }

    @Tool(description = "Search Zendesk using Zendesk search syntax (e.g. 'type:ticket status:open', 'type:ticket created>2026-01-01'). Supports sideloading related resources (users, organizations, groups) via 'include'.")
    public SearchResponse search(
            @ToolArg(description = "Zendesk search query string") String query,
            @ToolArg(description = "Optional resources to sideload. E.g. 'users,organizations,groups' (auto-wrapped in tickets(...)) or explicit 'tickets(users,organizations)'") @Nullable String include,
            @ToolArg(description = "Maximum number of results to return (default 25)") @Nullable Integer maxResults
    ) {
        validateSearchQuery(query);
        int limit = (maxResults != null && maxResults > 0) ? maxResults : 25;
        String resolvedInclude = resolveInclude(include);
        log.info("MCP Tool called: search(query='{}', include='{}', maxResults={})", query, resolvedInclude, limit);

        SearchResponse accumulatedResponse = new SearchResponse();
        accumulatedResponse.setResults(new ArrayList<>());
        accumulatedResponse.setUsers(new ArrayList<>());
        accumulatedResponse.setOrganizations(new ArrayList<>());
        accumulatedResponse.setGroups(new ArrayList<>());

        boolean hasMore = true;
        int page = 1;
        while (hasMore && accumulatedResponse.getResults().size() < limit) {
            SearchResponse pageResponse = searchClient.list(query, resolvedInclude, null, null, page, 100).block();
            if (pageResponse == null || pageResponse.getResults() == null || pageResponse.getResults().isEmpty()) {
                hasMore = false;
            } else {
                accumulatedResponse.getResults().addAll(pageResponse.getResults());
                if (pageResponse.getUsers() != null) accumulatedResponse.getUsers().addAll(pageResponse.getUsers());
                if (pageResponse.getOrganizations() != null) accumulatedResponse.getOrganizations().addAll(pageResponse.getOrganizations());
                if (pageResponse.getGroups() != null) accumulatedResponse.getGroups().addAll(pageResponse.getGroups());
                hasMore = pageResponse.getNextPage() != null;
                page++;
            }
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
        validateSearchQuery(query);
        log.info("MCP Tool called: searchCount(query='{}')", query);
        return searchClient.count(query).block();
    }
}
