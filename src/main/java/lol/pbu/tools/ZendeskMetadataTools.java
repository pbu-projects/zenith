package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.service.ZendeskMetadataService;
import lol.pbu.z4j.client.CustomStatusClient;
import lol.pbu.z4j.client.TicketFormsClient;
import lol.pbu.z4j.model.TicketFieldCustomStatusObject;
import lol.pbu.z4j.model.TicketForm;
import lol.pbu.z4j.model.TicketFormResponse;
import lol.pbu.z4j.model.TicketFormStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static lol.pbu.tools.ToolValidationSupport.validateKnownParameters;

@Singleton
public class ZendeskMetadataTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskMetadataTools.class);
    private static final String TICKET_FORMS_KEY = "ticket_forms";
    private static final String PARAM_TICKET_FORM_ID = "ticket_form_id";
    private static final String PARAM_TICKET_FORM_ID_CAMEL = "ticketFormId";
    private static final String KEY_DEFAULT = "default";
    private static final String KEY_CUSTOM_STATUSES = "custom_statuses";
    private static final String KEY_STATUS_CATEGORIES = "status_categories";

    private final ZendeskMetadataService metadataService;
    private final TicketFormsClient ticketFormsClient;
    private final CustomStatusClient customStatusClient;

    public ZendeskMetadataTools(ZendeskMetadataService metadataService, TicketFormsClient ticketFormsClient) {
        this(metadataService, ticketFormsClient, null);
    }

    @Inject
    public ZendeskMetadataTools(
            ZendeskMetadataService metadataService,
            TicketFormsClient ticketFormsClient,
            @Nullable CustomStatusClient customStatusClient
    ) {
        this.metadataService = metadataService;
        this.ticketFormsClient = ticketFormsClient;
        this.customStatusClient = customStatusClient;
    }

    public void clearCustomStatusCache() {
        metadataService.clearCustomStatusCache();
    }

    public void clearTicketFormCache() {
        metadataService.clearTicketFormCache();
    }

    public void clearAllCaches() {
        metadataService.clearAllCaches();
    }

    public List<TicketFieldCustomStatusObject> getCachedCustomStatuses(boolean forceRefresh) {
        return metadataService.getCachedCustomStatuses(forceRefresh);
    }

    public List<TicketFormStatus> getCachedTicketFormStatuses(boolean forceRefresh) {
        return metadataService.getCachedTicketFormStatuses(forceRefresh);
    }

    public List<TicketForm> getCachedTicketForms(boolean forceRefresh) {
        return metadataService.getCachedTicketForms(forceRefresh);
    }

    public Long resolveTicketFormId(Long ticketFormId, @Nullable CallToolRequest request) {
        return ToolValidationSupport.resolveTicketFormId(ticketFormId, request);
    }

    public void validateTicketFormBounds(Long ticketFormId) {
        ToolValidationSupport.validateTicketFormBounds(ticketFormId);
    }
    @Tool(description = "List Zendesk custom ticket statuses and status categories with context on which forms they can be used with. Supports filtering by statusCategory and ticketFormId. By default returns a compact summary of active statuses to save tokens.")
    public Map<String, Object> listCustomStatuses(
            @ToolArg(description = "Optional filter by status category: new, open, pending, hold, solved") @Nullable String statusCategory,
            @ToolArg(description = "Optional filter by numeric ticket form ID to return only custom statuses available for that specific form") @Nullable Long ticketFormId,
            @ToolArg(description = "Whether to include inactive custom statuses. Defaults to false (active statuses only)") @Nullable Boolean includeInactive,
            @ToolArg(description = "Whether to return full status objects or just compact summary. Defaults to false (summary mode)") @Nullable Boolean fullPayload,
            @Nullable CallToolRequest request
    ) {
        log.info("MCP Tool called: listCustomStatuses(statusCategory='{}', ticketFormId={}, includeInactive={}, fullPayload={})", statusCategory, ticketFormId, includeInactive, fullPayload);
        validateKnownParameters(request, "listCustomStatuses", "statusCategory", PARAM_TICKET_FORM_ID_CAMEL, PARAM_TICKET_FORM_ID, "includeInactive", "fullPayload");

        Long resolvedTicketFormId = resolveOptionalTicketFormId(ticketFormId, request);
        String normalizedCategory = validateAndNormalizeCategory(statusCategory);

        if (customStatusClient == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put(KEY_CUSTOM_STATUSES, Collections.emptyList());
            empty.put(KEY_STATUS_CATEGORIES, Collections.emptyMap());
            return empty;
        }

        List<TicketFieldCustomStatusObject> allStatuses = getCachedCustomStatuses(false);
        List<TicketFormStatus> allFormStatuses = getCachedTicketFormStatuses(false);
        Map<Long, List<Long>> statusToForms = buildStatusToFormsMap(allFormStatuses);
        List<TicketFieldCustomStatusObject> statuses = filterCustomStatuses(allStatuses, includeInactive, normalizedCategory, resolvedTicketFormId, statusToForms);
        Map<String, List<Map<String, Object>>> categoryMap = buildCategoryMap(statuses, normalizedCategory, statusToForms);

        Map<String, Object> result = new LinkedHashMap<>();
        if (resolvedTicketFormId != null) {
            result.put(PARAM_TICKET_FORM_ID, resolvedTicketFormId);
        }

        if (Boolean.TRUE.equals(fullPayload)) {
            result.put(KEY_CUSTOM_STATUSES, statuses);
            result.put(KEY_STATUS_CATEGORIES, categoryMap);
            if (allFormStatuses != null && !allFormStatuses.isEmpty()) {
                result.put("ticket_form_statuses", allFormStatuses);
            }
            return result;
        }

        result.put(KEY_CUSTOM_STATUSES, buildStatusSummaries(statuses, statusToForms));
        result.put(KEY_STATUS_CATEGORIES, categoryMap);
        return result;
    }

    private Long resolveOptionalTicketFormId(@Nullable Long ticketFormId, @Nullable CallToolRequest request) {
        if (ticketFormId != null) {
            return ticketFormId;
        }
        if (request != null && request.arguments() != null) {
            if (request.arguments().containsKey(PARAM_TICKET_FORM_ID)) {
                return parseOptionalFormId(request.arguments().get(PARAM_TICKET_FORM_ID));
            } else if (request.arguments().containsKey(PARAM_TICKET_FORM_ID_CAMEL)) {
                return parseOptionalFormId(request.arguments().get(PARAM_TICKET_FORM_ID_CAMEL));
            }
        }
        return null;
    }

    private Long parseOptionalFormId(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        } else if (val != null) {
            try {
                return Long.parseLong(val.toString().trim());
            } catch (NumberFormatException _) {
                // Ignore non-numeric ticket form ID in list filter
            }
        }
        return null;
    }

    private String validateAndNormalizeCategory(@Nullable String statusCategory) {
        if (statusCategory != null && !statusCategory.isBlank()) {
            String normalizedCategory = statusCategory.trim().toLowerCase();
            Set<String> validCategories = Set.of("new", "open", "pending", "hold", "solved");
            if (!validCategories.contains(normalizedCategory)) {
                throw new IllegalArgumentException("Invalid status category: '" + statusCategory + "'. Allowed categories are: new, open, pending, hold, solved.");
            }
            return normalizedCategory;
        }
        return null;
    }

    private Map<Long, List<Long>> buildStatusToFormsMap(@Nullable List<TicketFormStatus> allFormStatuses) {
        Map<Long, List<Long>> statusToForms = new LinkedHashMap<>();
        if (allFormStatuses != null) {
            for (TicketFormStatus fs : allFormStatuses) {
                if (fs != null && fs.customStatusId() != null && fs.ticketFormId() != null) {
                    statusToForms.computeIfAbsent(fs.customStatusId(), k -> new ArrayList<>()).add(fs.ticketFormId());
                }
            }
        }
        return statusToForms;
    }

    private List<TicketFieldCustomStatusObject> filterCustomStatuses(
            List<TicketFieldCustomStatusObject> allStatuses,
            @Nullable Boolean includeInactive,
            @Nullable String normalizedCategory,
            @Nullable Long resolvedTicketFormId,
            Map<Long, List<Long>> statusToForms
    ) {
        List<TicketFieldCustomStatusObject> statuses = allStatuses;
        if (!Boolean.TRUE.equals(includeInactive)) {
            statuses = statuses.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getActive()))
                    .toList();
        }
        if (normalizedCategory != null) {
            final String cat = normalizedCategory;
            statuses = statuses.stream()
                    .filter(s -> s.getStatusCategory() != null && cat.equalsIgnoreCase(s.getStatusCategory().getValue()))
                    .toList();
        }
        if (resolvedTicketFormId != null) {
            final Long formId = resolvedTicketFormId;
            statuses = statuses.stream().filter(s -> {
                if (s == null) return false;
                if (Boolean.TRUE.equals(s.getIsDefault())) return true;
                if (statusToForms.isEmpty()) return true;
                List<Long> forms = statusToForms.get(s.getId());
                return forms == null || forms.contains(formId);
            }).toList();
        }
        return statuses;
    }

    private Map<String, List<Map<String, Object>>> buildCategoryMap(
            List<TicketFieldCustomStatusObject> statuses,
            @Nullable String normalizedCategory,
            Map<Long, List<Long>> statusToForms
    ) {
        Map<String, List<Map<String, Object>>> categoryMap = new LinkedHashMap<>();
        List<String> stdCategories = List.of("new", "open", "pending", "hold", "solved");
        for (String cat : stdCategories) {
            if (normalizedCategory != null && !normalizedCategory.equalsIgnoreCase(cat)) {
                continue;
            }
            categoryMap.put(cat, new ArrayList<>());
        }
        for (TicketFieldCustomStatusObject s : statuses) {
            String cat = s.getStatusCategory() != null ? s.getStatusCategory().getValue().toLowerCase() : "unknown";
            List<Map<String, Object>> catList = categoryMap.computeIfAbsent(cat, k -> new ArrayList<>());
            Map<String, Object> catItem = new LinkedHashMap<>();
            catItem.put("id", s.getId());
            catItem.put("agent_label", s.getAgentLabel());
            catItem.put(KEY_DEFAULT, s.getIsDefault());
            if (statusToForms.containsKey(s.getId())) {
                catItem.put("ticket_form_ids", statusToForms.get(s.getId()));
            } else {
                catItem.put("applies_to_all_forms", true);
            }
            catList.add(catItem);
        }
        return categoryMap;
    }

    private List<Map<String, Object>> buildStatusSummaries(
            List<TicketFieldCustomStatusObject> statuses,
            Map<Long, List<Long>> statusToForms
    ) {
        return statuses.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("agent_label", s.getAgentLabel());
            item.put("status_category", s.getStatusCategory() != null ? s.getStatusCategory().getValue() : null);
            item.put("active", s.getActive());
            item.put(KEY_DEFAULT, s.getIsDefault());
            if (statusToForms.containsKey(s.getId())) {
                item.put("ticket_form_ids", statusToForms.get(s.getId()));
            } else {
                item.put("applies_to_all_forms", true);
            }
            if (StringUtils.isNotEmpty(s.getDescription())) {
                item.put("description", s.getDescription());
            }
            return item;
        }).toList();
    }

    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload,
            @Nullable CallToolRequest request
    ) {
        return listCustomStatuses(statusCategory, null, includeInactive, fullPayload, request);
    }

    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Long ticketFormId,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload
    ) {
        return listCustomStatuses(statusCategory, ticketFormId, includeInactive, fullPayload, null);
    }

    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload
    ) {
        return listCustomStatuses(statusCategory, null, includeInactive, fullPayload, null);
    }

    public Map<String, Object> listCustomStatuses() {
        return listCustomStatuses(null, null, null, null, null);
    }

    public Map<String, Object> listStatusCategories() {
        return listCustomStatuses();
    }

    public Map<String, Object> listStatusCategories(@Nullable Long ticketFormId) {
        return listCustomStatuses(null, ticketFormId, false, false, null);
    }

    public Map<String, Object> listTicketForms() {
        return listTicketForms(null, null);
    }

    @Tool(description = "List Zendesk ticket forms. By default returns a compact summary of active forms only to save tokens. Use getTicketForm for full form details.")
    public Map<String, Object> listTicketForms(
            @ToolArg(description = "Whether to include inactive forms. Defaults to false (active forms only)") @Nullable Boolean includeInactive,
            @ToolArg(description = "Whether to return full form schemas (including conditions and field IDs) or just summary. Defaults to false (summary mode)") @Nullable Boolean fullPayload
    ) {
        log.info("MCP Tool called: listTicketForms(includeInactive={}, fullPayload={})", includeInactive, fullPayload);
        List<TicketForm> allForms = getCachedTicketForms(false);
        if (allForms == null || allForms.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put(TICKET_FORMS_KEY, Collections.emptyList());
            return empty;
        }

        boolean activeOnly = !Boolean.TRUE.equals(includeInactive);
        boolean isFull = Boolean.TRUE.equals(fullPayload);

        List<TicketForm> forms = new ArrayList<>(allForms);
        if (activeOnly) {
            forms = forms.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getActive()))
                    .toList();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (isFull) {
            result.put(TICKET_FORMS_KEY, forms);
            return result;
        }

        List<Map<String, Object>> summaries = forms.stream().map(f -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", f.getId());
            s.put("name", f.getName());
            s.put("display_name", f.getDisplayName());
            s.put("active", f.getActive());
            s.put(KEY_DEFAULT, f.getDefaultForm());
            return s;
        }).toList();

        result.put(TICKET_FORMS_KEY, summaries);
        return result;
    }

    @Tool(description = "Get details of a specific Zendesk ticket form by its numeric ID")
    public Mono<TicketFormResponse> getTicketForm(
            @ToolArg(description = "The numeric ticket form ID") Long ticketFormId
    ) {
        log.info("MCP Tool called: getTicketForm(id={})", ticketFormId);
        if (ticketFormId == null) {
            throw new IllegalArgumentException(PARAM_TICKET_FORM_ID_CAMEL + " is required");
        }
        validateTicketFormBounds(ticketFormId);
        return ticketFormsClient.showTicketForm(ticketFormId);
    }


}
