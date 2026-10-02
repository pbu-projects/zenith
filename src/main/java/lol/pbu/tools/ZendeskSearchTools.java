package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.model.SearchResponse;
import lol.pbu.z4j.model.Group;
import lol.pbu.z4j.model.Organization;
import lol.pbu.z4j.model.SearchResult;
import lol.pbu.z4j.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public Mono<SearchResponse> search(
            @ToolArg(description = "Zendesk search query string") String query,
            @ToolArg(description = "Optional resources to sideload. E.g. 'users,organizations,groups' (auto-wrapped in tickets(...)) or explicit 'tickets(users,organizations)'") @Nullable String include,
            @ToolArg(description = "Maximum number of results to return (default 25)") @Nullable Integer maxResults
    ) {
        validateSearchQuery(query);
        int limit = (maxResults != null && maxResults > 0) ? maxResults : 25;
        String resolvedInclude = resolveInclude(include);
        log.info("MCP Tool called: search(query='{}', include='{}', maxResults={})", query, resolvedInclude, limit);

        return fetchSearchPage(query, resolvedInclude, 1, limit)
                .map(response -> finalizeResponse(response, limit));
    }

    private Mono<SearchResponse> fetchSearchPage(
            String query,
            String resolvedInclude,
            int page,
            int limit
    ) {
        return searchClient.list(query, resolvedInclude, null, null, page, 100)
                .flatMap(pageResponse -> {
                    if (isPageEmpty(pageResponse)) {
                        return Mono.just(emptySearchResponse());
                    }
                    int currentCount = pageResponse.getResults().size();
                    if (pageResponse.getNextPage() != null && currentCount < limit) {
                        return fetchSearchPage(query, resolvedInclude, page + 1, limit - currentCount)
                                .map(nextPageResponse -> combineResponses(pageResponse, nextPageResponse));
                    }
                    return Mono.just(pageResponse);
                })
                .defaultIfEmpty(emptySearchResponse());
    }

    private SearchResponse emptySearchResponse() {
        SearchResponse response = new SearchResponse();
        response.setResults(Collections.emptyList());
        response.setUsers(Collections.emptyList());
        response.setOrganizations(Collections.emptyList());
        response.setGroups(Collections.emptyList());
        response.setCount(0);
        return response;
    }

    private SearchResponse combineResponses(SearchResponse first, SearchResponse second) {
        SearchResponse combined = new SearchResponse();
        List<SearchResult> results = new ArrayList<>(first.getResults() != null ? first.getResults() : Collections.emptyList());
        if (second.getResults() != null) {
            results.addAll(second.getResults());
        }
        combined.setResults(results);

        List<User> users = new ArrayList<>(first.getUsers() != null ? first.getUsers() : Collections.emptyList());
        if (second.getUsers() != null) {
            users.addAll(second.getUsers());
        }
        combined.setUsers(users);

        List<Organization> orgs = new ArrayList<>(first.getOrganizations() != null ? first.getOrganizations() : Collections.emptyList());
        if (second.getOrganizations() != null) {
            orgs.addAll(second.getOrganizations());
        }
        combined.setOrganizations(orgs);

        List<Group> groups = new ArrayList<>(first.getGroups() != null ? first.getGroups() : Collections.emptyList());
        if (second.getGroups() != null) {
            groups.addAll(second.getGroups());
        }
        combined.setGroups(groups);

        return combined;
    }

    private SearchResponse finalizeResponse(SearchResponse response, int limit) {
        List<SearchResult> results = response.getResults() != null ? response.getResults() : Collections.emptyList();
        if (results.size() > limit) {
            response.setResults(new ArrayList<>(results.subList(0, limit)));
        }
        response.setCount(response.getResults().size());
        return response;
    }

    @Tool(description = "Get the count of search results matching a query in Zendesk")
    public Mono<SearchResponse> searchCount(
            @ToolArg(description = "Zendesk search query string, e.g. 'type:ticket status:open'") String query
    ) {
        validateSearchQuery(query);
        log.info("MCP Tool called: searchCount(query='{}')", query);
        return searchClient.count(query);
    }

    private boolean isPageEmpty(SearchResponse pageResponse) {
        return pageResponse == null || pageResponse.getResults() == null || pageResponse.getResults().isEmpty();
    }
}
