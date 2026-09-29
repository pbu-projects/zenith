package lol.pbu.tools;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.CustomObjectRecordsClient;
import lol.pbu.z4j.client.CustomObjectsClient;
import lol.pbu.z4j.model.CustomObjectRecordResponse;
import lol.pbu.z4j.model.CustomObjectRecordsResponse;
import lol.pbu.z4j.model.CustomObjectResponse;
import lol.pbu.z4j.model.CustomObjectsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ZendeskCustomObjectTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskCustomObjectTools.class);
    private final CustomObjectsClient customObjectsClient;
    private final CustomObjectRecordsClient customObjectRecordsClient;

    @Inject
    public ZendeskCustomObjectTools(CustomObjectsClient customObjectsClient, CustomObjectRecordsClient customObjectRecordsClient) {
        this.customObjectsClient = customObjectsClient;
        this.customObjectRecordsClient = customObjectRecordsClient;
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
