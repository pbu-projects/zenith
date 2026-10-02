package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.CustomObjectRecordsClient;
import lol.pbu.z4j.client.CustomObjectsClient;
import lol.pbu.z4j.model.CustomObject;
import lol.pbu.z4j.model.CustomObjectLimitsResponse;
import lol.pbu.z4j.model.CustomObjectRecord;
import lol.pbu.z4j.model.CustomObjectRecordResponse;
import lol.pbu.z4j.model.CustomObjectRecordsCreateRequest;
import lol.pbu.z4j.model.CustomObjectRecordsResponse;
import lol.pbu.z4j.model.CustomObjectResponse;
import lol.pbu.z4j.model.CustomObjectsCreateRequest;
import lol.pbu.z4j.model.CustomObjectsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import static lol.pbu.tools.ToolValidationSupport.validateKnownParameters;

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
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        return customObjectsClient.showCustomObject(customObjectKey).block();
    }

    @Tool(description = "Create a new custom object definition in Zendesk")
    public CustomObjectResponse createCustomObject(
            @ToolArg(description = "A unique key to identify the custom object (e.g. 'car', 'device')") @Nullable String key,
            @ToolArg(description = "Singular display title for the custom object (e.g. 'Car')") @Nullable String title,
            @ToolArg(description = "Plural display title for the custom object (e.g. 'Cars')") @Nullable String titlePluralized,
            @ToolArg(description = "Optional description for the custom object") @Nullable String description,
            @ToolArg(description = "Optional nested custom object definition payload") @Nullable Map<String, Object> customObject,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: createCustomObject(key='{}', title='{}')", key, title);
        validateKnownParameters(request, "createCustomObject", "key", "title", "titlePluralized", "title_pluralized", "description", "customObject", "custom_object");
        CustomObject obj = resolveCustomObject(key, title, titlePluralized, description, customObject, request);
        if (obj.getKey() == null || obj.getKey().isBlank()) {
            throw new IllegalArgumentException("key is required when creating a custom object.");
        }
        if (obj.getTitle() == null || obj.getTitle().isBlank()) {
            throw new IllegalArgumentException("title is required when creating a custom object.");
        }
        if (obj.getTitlePluralized() == null || obj.getTitlePluralized().isBlank()) {
            throw new IllegalArgumentException("titlePluralized is required when creating a custom object.");
        }
        CustomObjectsCreateRequest req = new CustomObjectsCreateRequest(obj);
        return customObjectsClient.createCustomObject(req).block();
    }

    public CustomObjectResponse createCustomObject(CustomObjectsCreateRequest customObjectRequest) {
        if (customObjectRequest == null || customObjectRequest.getCustomObject() == null) {
            throw new IllegalArgumentException("customObject is required");
        }
        return customObjectsClient.createCustomObject(customObjectRequest).block();
    }

    @Tool(description = "Update an existing custom object definition in Zendesk by its key")
    public CustomObjectResponse updateCustomObject(
            @ToolArg(description = "The key of the custom object to update") String customObjectKey,
            @ToolArg(description = "Updated singular display title for the custom object") @Nullable String title,
            @ToolArg(description = "Updated plural display title for the custom object") @Nullable String titlePluralized,
            @ToolArg(description = "Updated description for the custom object") @Nullable String description,
            @ToolArg(description = "Optional nested custom object definition payload") @Nullable Map<String, Object> customObject,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: updateCustomObject(key='{}')", customObjectKey);
        validateKnownParameters(request, "updateCustomObject", "customObjectKey", "custom_object_key", "title", "titlePluralized", "title_pluralized", "description", "customObject", "custom_object");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when updating a custom object.");
        }
        CustomObject obj = resolveCustomObject(null, title, titlePluralized, description, customObject, request);
        CustomObjectsCreateRequest req = new CustomObjectsCreateRequest(obj);
        return customObjectsClient.updateCustomObject(customObjectKey, req).block();
    }

    public CustomObjectResponse updateCustomObject(String customObjectKey, CustomObjectsCreateRequest customObjectRequest) {
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        if (customObjectRequest == null) {
            throw new IllegalArgumentException("customObject is required");
        }
        return customObjectsClient.updateCustomObject(customObjectKey, customObjectRequest).block();
    }

    @Tool(description = "Delete a custom object definition from Zendesk by its key")
    public Map<String, Object> deleteCustomObject(
            @ToolArg(description = "The key of the custom object to delete") String customObjectKey,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: deleteCustomObject(key='{}')", customObjectKey);
        validateKnownParameters(request, "deleteCustomObject", "customObjectKey", "custom_object_key");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when deleting a custom object.");
        }
        customObjectsClient.deleteCustomObject(customObjectKey).block();
        return Map.of("success", true, "deletedCustomObjectKey", customObjectKey);
    }

    public Map<String, Object> deleteCustomObject(String customObjectKey) {
        return deleteCustomObject(customObjectKey, null);
    }

    @Tool(description = "Get custom object limits for the Zendesk account")
    public CustomObjectLimitsResponse getCustomObjectLimits() {
        log.info("MCP Tool called: getCustomObjectLimits()");
        return customObjectsClient.customObjectsLimit().block();
    }

    @Tool(description = "List records for a specific Zendesk custom object")
    public CustomObjectRecordsResponse listCustomObjectRecords(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "Optional comma-separated record IDs to filter by") @Nullable String filterIds,
            @ToolArg(description = "Optional comma-separated external IDs to filter by") @Nullable String filterExternalIds,
            @ToolArg(description = "Optional sort parameter") @Nullable String sort,
            @ToolArg(description = "Optional cursor to fetch records before") @Nullable String pageBefore,
            @ToolArg(description = "Optional cursor to fetch records after") @Nullable String pageAfter,
            @ToolArg(description = "Optional page size") @Nullable Long pageSize,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: listCustomObjectRecords(key='{}')", customObjectKey);
        validateKnownParameters(request, "listCustomObjectRecords", "customObjectKey", "custom_object_key", "filterIds", "filter_ids", "filterExternalIds", "filter_external_ids", "sort", "pageBefore", "page_before", "pageAfter", "page_after", "pageSize", "page_size");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        return customObjectRecordsClient.listCustomObjectRecords(customObjectKey, filterIds, filterExternalIds, sort, pageBefore, pageAfter, pageSize).block();
    }

    public CustomObjectRecordsResponse listCustomObjectRecords(String customObjectKey) {
        return listCustomObjectRecords(customObjectKey, null, null, null, null, null, null, null);
    }

    @Tool(description = "Get details of a specific custom object record by its ID")
    public CustomObjectRecordResponse getCustomObjectRecord(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "The ID of the custom object record") String recordId
    ) {
        log.info("MCP Tool called: getCustomObjectRecord(key='{}', recordId='{}')", customObjectKey, recordId);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required");
        }
        return customObjectRecordsClient.showCustomObjectRecord(customObjectKey, recordId).block();
    }

    @Tool(description = "Create a new record for a Zendesk custom object")
    public CustomObjectRecordResponse createCustomObjectRecord(
            @ToolArg(description = "The key of the custom object (e.g. 'car')") String customObjectKey,
            @ToolArg(description = "Optional name or title for the record") @Nullable String name,
            @ToolArg(description = "Optional key-value map of custom object field values") @Nullable Map<String, Object> customObjectFields,
            @ToolArg(description = "Optional external ID for the record") @Nullable String externalId,
            @ToolArg(description = "Optional nested custom object record payload") @Nullable Map<String, Object> customObjectRecord,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: createCustomObjectRecord(key='{}', name='{}')", customObjectKey, name);
        validateKnownParameters(request, "createCustomObjectRecord", "customObjectKey", "custom_object_key", "name", "customObjectFields", "custom_object_fields", "externalId", "external_id", "customObjectRecord", "custom_object_record");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when creating a custom object record.");
        }
        CustomObjectRecord record = resolveCustomObjectRecord(name, customObjectFields, externalId, customObjectRecord, request);
        CustomObjectRecordsCreateRequest req = new CustomObjectRecordsCreateRequest(record);
        return customObjectRecordsClient.createCustomObjectRecord(customObjectKey, req).block();
    }

    public CustomObjectRecordResponse createCustomObjectRecord(String customObjectKey, CustomObjectRecordsCreateRequest createRequest) {
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        if (createRequest == null || createRequest.getCustomObjectRecord() == null) {
            throw new IllegalArgumentException("customObjectRecord is required");
        }
        return customObjectRecordsClient.createCustomObjectRecord(customObjectKey, createRequest).block();
    }

    @Tool(description = "Update an existing custom object record by its custom object key and record ID")
    public CustomObjectRecordResponse updateCustomObjectRecord(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "The ID of the custom object record to update") String recordId,
            @ToolArg(description = "Optional updated name or title for the record") @Nullable String name,
            @ToolArg(description = "Optional updated key-value map of custom object field values") @Nullable Map<String, Object> customObjectFields,
            @ToolArg(description = "Optional updated external ID for the record") @Nullable String externalId,
            @ToolArg(description = "Optional nested custom object record payload") @Nullable Map<String, Object> customObjectRecord,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: updateCustomObjectRecord(key='{}', recordId='{}')", customObjectKey, recordId);
        validateKnownParameters(request, "updateCustomObjectRecord", "customObjectKey", "custom_object_key", "recordId", "record_id", "custom_object_record_id", "name", "customObjectFields", "custom_object_fields", "externalId", "external_id", "customObjectRecord", "custom_object_record");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when updating a custom object record.");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required when updating a custom object record.");
        }
        CustomObjectRecord record = resolveCustomObjectRecord(name, customObjectFields, externalId, customObjectRecord, request);
        CustomObjectRecordsCreateRequest req = new CustomObjectRecordsCreateRequest(record);
        return customObjectRecordsClient.updateCustomObjectRecord(customObjectKey, recordId, req).block();
    }

    public CustomObjectRecordResponse updateCustomObjectRecord(String customObjectKey, String recordId, CustomObjectRecordsCreateRequest updateRequest) {
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required");
        }
        if (updateRequest == null) {
            throw new IllegalArgumentException("customObjectRecord is required");
        }
        return customObjectRecordsClient.updateCustomObjectRecord(customObjectKey, recordId, updateRequest).block();
    }

    @Tool(description = "Delete a specific custom object record by custom object key and record ID")
    public Map<String, Object> deleteCustomObjectRecord(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "The ID of the custom object record to delete") String recordId,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: deleteCustomObjectRecord(key='{}', recordId='{}')", customObjectKey, recordId);
        validateKnownParameters(request, "deleteCustomObjectRecord", "customObjectKey", "custom_object_key", "recordId", "record_id", "custom_object_record_id");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when deleting a custom object record.");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required when deleting a custom object record.");
        }
        customObjectRecordsClient.deleteCustomObjectRecord(customObjectKey, recordId).block();
        return Map.of("success", true, "customObjectKey", customObjectKey, "deletedRecordId", recordId);
    }

    public Map<String, Object> deleteCustomObjectRecord(String customObjectKey, String recordId) {
        return deleteCustomObjectRecord(customObjectKey, recordId, null);
    }

    @Tool(description = "Search records for a specific Zendesk custom object matching query text")
    public CustomObjectRecordsResponse searchCustomObjectRecords(
            @ToolArg(description = "The key of the custom object") String customObjectKey,
            @ToolArg(description = "Search query string") String query,
            @ToolArg(description = "Optional sort parameter") @Nullable String sort,
            @ToolArg(description = "Optional cursor to fetch records before") @Nullable String pageBefore,
            @ToolArg(description = "Optional cursor to fetch records after") @Nullable String pageAfter,
            @ToolArg(description = "Optional page size") @Nullable Long pageSize,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: searchCustomObjectRecords(key='{}', query='{}')", customObjectKey, query);
        validateKnownParameters(request, "searchCustomObjectRecords", "customObjectKey", "custom_object_key", "query", "sort", "pageBefore", "page_before", "pageAfter", "page_after", "pageSize", "page_size");
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        return customObjectRecordsClient.searchCustomObjectRecords(customObjectKey, query, sort, pageBefore, pageAfter, pageSize).block();
    }

    public CustomObjectRecordsResponse searchCustomObjectRecords(String customObjectKey, String query) {
        return searchCustomObjectRecords(customObjectKey, query, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private CustomObject resolveCustomObject(
            @Nullable String key,
            @Nullable String title,
            @Nullable String titlePluralized,
            @Nullable String description,
            @Nullable Map<String, Object> customObjectMap,
            @Nullable CallToolRequest request
    ) {
        CustomObject obj = new CustomObject();
        obj.setKey(key);
        obj.setTitle(title);
        obj.setTitlePluralized(titlePluralized);
        obj.setDescription(description);

        Map<String, Object> args = request != null && request.arguments() != null ? request.arguments() : Collections.emptyMap();

        if (customObjectMap == null) {
            Object nested = args.get("custom_object");
            if (nested instanceof Map<?, ?> m) {
                customObjectMap = (Map<String, Object>) m;
            } else {
                nested = args.get("customObject");
                if (nested instanceof Map<?, ?> m) {
                    customObjectMap = (Map<String, Object>) m;
                }
            }
        }

        if (customObjectMap != null) {
            if (obj.getKey() == null && customObjectMap.get("key") != null) {
                obj.setKey(String.valueOf(customObjectMap.get("key")));
            }
            if (obj.getTitle() == null && customObjectMap.get("title") != null) {
                obj.setTitle(String.valueOf(customObjectMap.get("title")));
            }
            if (obj.getTitlePluralized() == null) {
                Object tp = customObjectMap.get("title_pluralized");
                if (tp == null) tp = customObjectMap.get("titlePluralized");
                if (tp != null) obj.setTitlePluralized(String.valueOf(tp));
            }
            if (obj.getDescription() == null && customObjectMap.get("description") != null) {
                obj.setDescription(String.valueOf(customObjectMap.get("description")));
            }
        }

        if (obj.getTitlePluralized() == null && args.containsKey("title_pluralized") && args.get("title_pluralized") != null) {
            obj.setTitlePluralized(String.valueOf(args.get("title_pluralized")));
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private CustomObjectRecord resolveCustomObjectRecord(
            @Nullable String name,
            @Nullable Map<String, Object> customObjectFields,
            @Nullable String externalId,
            @Nullable Map<String, Object> customObjectRecordMap,
            @Nullable CallToolRequest request
    ) {
        CustomObjectRecord record = new CustomObjectRecord();
        record.setName(name);
        record.setCustomObjectFields(customObjectFields);
        record.setExternalId(externalId);

        Map<String, Object> args = request != null && request.arguments() != null ? request.arguments() : Collections.emptyMap();

        if (customObjectRecordMap == null) {
            Object nested = args.get("custom_object_record");
            if (nested instanceof Map<?, ?> m) {
                customObjectRecordMap = (Map<String, Object>) m;
            } else {
                nested = args.get("customObjectRecord");
                if (nested instanceof Map<?, ?> m) {
                    customObjectRecordMap = (Map<String, Object>) m;
                }
            }
        }

        if (customObjectRecordMap != null) {
            if (record.getName() == null && customObjectRecordMap.get("name") != null) {
                record.setName(String.valueOf(customObjectRecordMap.get("name")));
            }
            if (record.getExternalId() == null) {
                Object extId = customObjectRecordMap.get("external_id");
                if (extId == null) extId = customObjectRecordMap.get("externalId");
                if (extId != null) record.setExternalId(String.valueOf(extId));
            }
            if (record.getCustomObjectFields() == null) {
                Object fields = customObjectRecordMap.get("custom_object_fields");
                if (fields == null) fields = customObjectRecordMap.get("customObjectFields");
                if (fields instanceof Map<?, ?> fieldsMap) {
                    record.setCustomObjectFields((Map<String, Object>) fieldsMap);
                }
            }
        }

        if (record.getCustomObjectFields() == null && args.containsKey("custom_object_fields") && args.get("custom_object_fields") instanceof Map<?, ?> m) {
            record.setCustomObjectFields((Map<String, Object>) m);
        } else if (record.getCustomObjectFields() == null && args.containsKey("customObjectFields") && args.get("customObjectFields") instanceof Map<?, ?> m) {
            record.setCustomObjectFields((Map<String, Object>) m);
        }
        if (record.getExternalId() == null && args.containsKey("external_id") && args.get("external_id") != null) {
            record.setExternalId(String.valueOf(args.get("external_id")));
        } else if (record.getExternalId() == null && args.containsKey("externalId") && args.get("externalId") != null) {
            record.setExternalId(String.valueOf(args.get("externalId")));
        }

        return record;
    }
}
