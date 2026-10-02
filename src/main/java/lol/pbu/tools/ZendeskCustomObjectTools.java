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
@SuppressWarnings("java:S107")
public class ZendeskCustomObjectTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskCustomObjectTools.class);

    private static final String PARAM_CUSTOM_OBJECT_KEY = "customObjectKey";
    private static final String PARAM_CUSTOM_OBJECT_KEY_SNAKE = "custom_object_key";
    private static final String PARAM_KEY = "key";
    private static final String PARAM_TITLE = "title";
    private static final String PARAM_TITLE_PLURALIZED = "titlePluralized";
    private static final String PARAM_TITLE_PLURALIZED_SNAKE = "title_pluralized";
    private static final String PARAM_DESCRIPTION = "description";
    private static final String PARAM_CUSTOM_OBJECT = "customObject";
    private static final String PARAM_CUSTOM_OBJECT_SNAKE = "custom_object";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_CUSTOM_OBJECT_FIELDS = "customObjectFields";
    private static final String PARAM_CUSTOM_OBJECT_FIELDS_SNAKE = "custom_object_fields";
    private static final String PARAM_EXTERNAL_ID = "externalId";
    private static final String PARAM_EXTERNAL_ID_SNAKE = "external_id";
    private static final String PARAM_CUSTOM_OBJECT_RECORD = "customObjectRecord";
    private static final String PARAM_CUSTOM_OBJECT_RECORD_SNAKE = "custom_object_record";
    private static final String PARAM_RECORD_ID = "recordId";
    private static final String PARAM_RECORD_ID_SNAKE = "record_id";
    private static final String PARAM_CUSTOM_OBJECT_RECORD_ID = "custom_object_record_id";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_SORT = "sort";
    private static final String PARAM_FILTER_IDS = "filterIds";
    private static final String PARAM_FILTER_IDS_SNAKE = "filter_ids";
    private static final String PARAM_FILTER_EXTERNAL_IDS = "filterExternalIds";
    private static final String PARAM_FILTER_EXTERNAL_IDS_SNAKE = "filter_external_ids";
    private static final String PARAM_PAGE_BEFORE = "pageBefore";
    private static final String PARAM_PAGE_BEFORE_SNAKE = "page_before";
    private static final String PARAM_PAGE_AFTER = "pageAfter";
    private static final String PARAM_PAGE_AFTER_SNAKE = "page_after";
    private static final String PARAM_PAGE_SIZE = "pageSize";
    private static final String PARAM_PAGE_SIZE_SNAKE = "page_size";

    private static final String MSG_CUSTOM_OBJECT_KEY_REQUIRED = "customObjectKey is required";
    private static final String MSG_CUSTOM_OBJECT_REQUIRED = "customObject is required";
    private static final String MSG_RECORD_ID_REQUIRED = "recordId is required";
    private static final String MSG_CUSTOM_OBJECT_RECORD_REQUIRED = "customObjectRecord is required";
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
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
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
        validateKnownParameters(request, "createCustomObject", PARAM_KEY, PARAM_TITLE, PARAM_TITLE_PLURALIZED, PARAM_TITLE_PLURALIZED_SNAKE, PARAM_DESCRIPTION, PARAM_CUSTOM_OBJECT, PARAM_CUSTOM_OBJECT_SNAKE);
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
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_REQUIRED);
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
        validateKnownParameters(request, "updateCustomObject", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE, PARAM_TITLE, PARAM_TITLE_PLURALIZED, PARAM_TITLE_PLURALIZED_SNAKE, PARAM_DESCRIPTION, PARAM_CUSTOM_OBJECT, PARAM_CUSTOM_OBJECT_SNAKE);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when updating a custom object.");
        }
        CustomObject obj = resolveCustomObject(null, title, titlePluralized, description, customObject, request);
        CustomObjectsCreateRequest req = new CustomObjectsCreateRequest(obj);
        return customObjectsClient.updateCustomObject(customObjectKey, req).block();
    }

    public CustomObjectResponse updateCustomObject(String customObjectKey, CustomObjectsCreateRequest customObjectRequest) {
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
        }
        if (customObjectRequest == null) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_REQUIRED);
        }
        return customObjectsClient.updateCustomObject(customObjectKey, customObjectRequest).block();
    }

    @Tool(description = "Delete a custom object definition from Zendesk by its key")
    public Map<String, Object> deleteCustomObject(
            @ToolArg(description = "The key of the custom object to delete") String customObjectKey,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: deleteCustomObject(key='{}')", customObjectKey);
        validateKnownParameters(request, "deleteCustomObject", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE);
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
        validateKnownParameters(request, "listCustomObjectRecords", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE, PARAM_FILTER_IDS, PARAM_FILTER_IDS_SNAKE, PARAM_FILTER_EXTERNAL_IDS, PARAM_FILTER_EXTERNAL_IDS_SNAKE, PARAM_SORT, PARAM_PAGE_BEFORE, PARAM_PAGE_BEFORE_SNAKE, PARAM_PAGE_AFTER, PARAM_PAGE_AFTER_SNAKE, PARAM_PAGE_SIZE, PARAM_PAGE_SIZE_SNAKE);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
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
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException(MSG_RECORD_ID_REQUIRED);
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
        validateKnownParameters(request, "createCustomObjectRecord", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE, PARAM_NAME, PARAM_CUSTOM_OBJECT_FIELDS, PARAM_CUSTOM_OBJECT_FIELDS_SNAKE, PARAM_EXTERNAL_ID, PARAM_EXTERNAL_ID_SNAKE, PARAM_CUSTOM_OBJECT_RECORD, PARAM_CUSTOM_OBJECT_RECORD_SNAKE);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when creating a custom object record.");
        }
        CustomObjectRecord recordObj = resolveCustomObjectRecord(name, customObjectFields, externalId, customObjectRecord, request);
        CustomObjectRecordsCreateRequest req = new CustomObjectRecordsCreateRequest(recordObj);
        return customObjectRecordsClient.createCustomObjectRecord(customObjectKey, req).block();
    }

    public CustomObjectRecordResponse createCustomObjectRecord(String customObjectKey, CustomObjectRecordsCreateRequest createRequest) {
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
        }
        if (createRequest == null || createRequest.getCustomObjectRecord() == null) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_RECORD_REQUIRED);
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
        validateKnownParameters(request, "updateCustomObjectRecord", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE, PARAM_RECORD_ID, PARAM_RECORD_ID_SNAKE, PARAM_CUSTOM_OBJECT_RECORD_ID, PARAM_NAME, PARAM_CUSTOM_OBJECT_FIELDS, PARAM_CUSTOM_OBJECT_FIELDS_SNAKE, PARAM_EXTERNAL_ID, PARAM_EXTERNAL_ID_SNAKE, PARAM_CUSTOM_OBJECT_RECORD, PARAM_CUSTOM_OBJECT_RECORD_SNAKE);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when updating a custom object record.");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required when updating a custom object record.");
        }
        CustomObjectRecord recordObj = resolveCustomObjectRecord(name, customObjectFields, externalId, customObjectRecord, request);
        CustomObjectRecordsCreateRequest req = new CustomObjectRecordsCreateRequest(recordObj);
        return customObjectRecordsClient.updateCustomObjectRecord(customObjectKey, recordId, req).block();
    }

    public CustomObjectRecordResponse updateCustomObjectRecord(String customObjectKey, String recordId, CustomObjectRecordsCreateRequest updateRequest) {
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException(MSG_RECORD_ID_REQUIRED);
        }
        if (updateRequest == null) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_RECORD_REQUIRED);
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
        validateKnownParameters(request, "deleteCustomObjectRecord", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE, PARAM_RECORD_ID, PARAM_RECORD_ID_SNAKE, PARAM_CUSTOM_OBJECT_RECORD_ID);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException("customObjectKey is required when deleting a custom object record.");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId is required when deleting a custom object record.");
        }
        customObjectRecordsClient.deleteCustomObjectRecord(customObjectKey, recordId).block();
        return Map.of("success", true, PARAM_CUSTOM_OBJECT_KEY, customObjectKey, "deletedRecordId", recordId);
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
        validateKnownParameters(request, "searchCustomObjectRecords", PARAM_CUSTOM_OBJECT_KEY, PARAM_CUSTOM_OBJECT_KEY_SNAKE, PARAM_QUERY, PARAM_SORT, PARAM_PAGE_BEFORE, PARAM_PAGE_BEFORE_SNAKE, PARAM_PAGE_AFTER, PARAM_PAGE_AFTER_SNAKE, PARAM_PAGE_SIZE, PARAM_PAGE_SIZE_SNAKE);
        if (customObjectKey == null || customObjectKey.isBlank()) {
            throw new IllegalArgumentException(MSG_CUSTOM_OBJECT_KEY_REQUIRED);
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        return customObjectRecordsClient.searchCustomObjectRecords(customObjectKey, query, sort, pageBefore, pageAfter, pageSize).block();
    }

    public CustomObjectRecordsResponse searchCustomObjectRecords(String customObjectKey, String query) {
        return searchCustomObjectRecords(customObjectKey, query, null, null, null, null, null);
    }

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
        Map<String, Object> resolvedMap = resolveNestedCustomObjectMap(customObjectMap, args);
        applyCustomObjectMap(obj, resolvedMap);
        applyTopLevelCustomObjectArgs(obj, args);
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveNestedCustomObjectMap(@Nullable Map<String, Object> customObjectMap, Map<String, Object> args) {
        if (customObjectMap != null) {
            return customObjectMap;
        }
        Object nested = args.get(PARAM_CUSTOM_OBJECT_SNAKE);
        if (nested instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        nested = args.get(PARAM_CUSTOM_OBJECT);
        if (nested instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Collections.emptyMap();
    }

    private void applyCustomObjectMap(CustomObject obj, Map<String, Object> customObjectMap) {
        if (customObjectMap == null || customObjectMap.isEmpty()) {
            return;
        }
        if (obj.getKey() == null && customObjectMap.get(PARAM_KEY) != null) {
            obj.setKey(String.valueOf(customObjectMap.get(PARAM_KEY)));
        }
        if (obj.getTitle() == null && customObjectMap.get(PARAM_TITLE) != null) {
            obj.setTitle(String.valueOf(customObjectMap.get(PARAM_TITLE)));
        }
        if (obj.getTitlePluralized() == null) {
            Object tp = customObjectMap.get(PARAM_TITLE_PLURALIZED_SNAKE);
            if (tp == null) {
                tp = customObjectMap.get(PARAM_TITLE_PLURALIZED);
            }
            if (tp != null) {
                obj.setTitlePluralized(String.valueOf(tp));
            }
        }
        if (obj.getDescription() == null && customObjectMap.get(PARAM_DESCRIPTION) != null) {
            obj.setDescription(String.valueOf(customObjectMap.get(PARAM_DESCRIPTION)));
        }
    }

    private void applyTopLevelCustomObjectArgs(CustomObject obj, Map<String, Object> args) {
        if (obj.getTitlePluralized() == null && args.containsKey(PARAM_TITLE_PLURALIZED_SNAKE) && args.get(PARAM_TITLE_PLURALIZED_SNAKE) != null) {
            obj.setTitlePluralized(String.valueOf(args.get(PARAM_TITLE_PLURALIZED_SNAKE)));
        }
    }

    private CustomObjectRecord resolveCustomObjectRecord(
            @Nullable String name,
            @Nullable Map<String, Object> customObjectFields,
            @Nullable String externalId,
            @Nullable Map<String, Object> customObjectRecordMap,
            @Nullable CallToolRequest request
    ) {
        CustomObjectRecord recordObj = new CustomObjectRecord();
        recordObj.setName(name);
        recordObj.setCustomObjectFields(customObjectFields);
        recordObj.setExternalId(externalId);

        Map<String, Object> args = request != null && request.arguments() != null ? request.arguments() : Collections.emptyMap();
        Map<String, Object> resolvedMap = resolveNestedCustomObjectRecordMap(customObjectRecordMap, args);
        applyCustomObjectRecordMap(recordObj, resolvedMap);
        applyTopLevelRecordFields(recordObj, args);
        applyTopLevelExternalId(recordObj, args);
        return recordObj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveNestedCustomObjectRecordMap(@Nullable Map<String, Object> customObjectRecordMap, Map<String, Object> args) {
        if (customObjectRecordMap != null) {
            return customObjectRecordMap;
        }
        Object nested = args.get(PARAM_CUSTOM_OBJECT_RECORD_SNAKE);
        if (nested instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        nested = args.get(PARAM_CUSTOM_OBJECT_RECORD);
        if (nested instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private void applyCustomObjectRecordMap(CustomObjectRecord recordObj, Map<String, Object> customObjectRecordMap) {
        if (customObjectRecordMap == null || customObjectRecordMap.isEmpty()) {
            return;
        }
        if (recordObj.getName() == null && customObjectRecordMap.get(PARAM_NAME) != null) {
            recordObj.setName(String.valueOf(customObjectRecordMap.get(PARAM_NAME)));
        }
        if (recordObj.getExternalId() == null) {
            Object extId = customObjectRecordMap.get(PARAM_EXTERNAL_ID_SNAKE);
            if (extId == null) {
                extId = customObjectRecordMap.get(PARAM_EXTERNAL_ID);
            }
            if (extId != null) {
                recordObj.setExternalId(String.valueOf(extId));
            }
        }
        if (recordObj.getCustomObjectFields() == null) {
            Object fields = customObjectRecordMap.get(PARAM_CUSTOM_OBJECT_FIELDS_SNAKE);
            if (fields == null) {
                fields = customObjectRecordMap.get(PARAM_CUSTOM_OBJECT_FIELDS);
            }
            if (fields instanceof Map<?, ?> fieldsMap) {
                recordObj.setCustomObjectFields((Map<String, Object>) fieldsMap);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyTopLevelRecordFields(CustomObjectRecord recordObj, Map<String, Object> args) {
        if (recordObj.getCustomObjectFields() != null) {
            return;
        }
        Object fields = args.get(PARAM_CUSTOM_OBJECT_FIELDS_SNAKE);
        if (fields instanceof Map<?, ?> m) {
            recordObj.setCustomObjectFields((Map<String, Object>) m);
            return;
        }
        fields = args.get(PARAM_CUSTOM_OBJECT_FIELDS);
        if (fields instanceof Map<?, ?> m) {
            recordObj.setCustomObjectFields((Map<String, Object>) m);
        }
    }

    private void applyTopLevelExternalId(CustomObjectRecord recordObj, Map<String, Object> args) {
        if (recordObj.getExternalId() != null) {
            return;
        }
        Object extId = args.get(PARAM_EXTERNAL_ID_SNAKE);
        if (extId == null) {
            extId = args.get(PARAM_EXTERNAL_ID);
        }
        if (extId != null) {
            recordObj.setExternalId(String.valueOf(extId));
        }
    }
}
