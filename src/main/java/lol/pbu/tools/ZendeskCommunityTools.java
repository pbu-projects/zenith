package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.PostClient;
import lol.pbu.z4j.client.TopicClient;
import lol.pbu.z4j.model.CommunityPostSearchResponse;
import lol.pbu.z4j.model.PostCommentsResponse;
import lol.pbu.z4j.model.PostResponse;
import lol.pbu.z4j.model.PostsResponse;
import lol.pbu.z4j.model.TopicResponse;
import lol.pbu.z4j.model.TopicsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ZendeskCommunityTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskCommunityTools.class);
    private final TopicClient topicClient;
    private final PostClient postClient;

    @Inject
    public ZendeskCommunityTools(TopicClient topicClient, PostClient postClient) {
        this.topicClient = topicClient;
        this.postClient = postClient;
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
}
