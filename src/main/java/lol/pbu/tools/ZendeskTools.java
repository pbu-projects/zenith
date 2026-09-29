package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.client.CustomStatusClient;
import lol.pbu.model.BatchUpdateResponse;
import lol.pbu.model.TicketFormStatus;
import lol.pbu.model.TicketMutationOptions;
import lol.pbu.service.ZendeskMetadataService;
import lol.pbu.z4j.client.ArticleClient;
import lol.pbu.z4j.client.AttachmentClient;
import lol.pbu.z4j.client.CategoryClient;
import lol.pbu.z4j.client.CustomObjectRecordsClient;
import lol.pbu.z4j.client.CustomObjectsClient;
import lol.pbu.z4j.client.JobStatusClient;
import lol.pbu.z4j.client.PostClient;
import lol.pbu.z4j.client.SearchClient;
import lol.pbu.z4j.client.TicketClient;
import lol.pbu.z4j.client.TicketFormsClient;
import lol.pbu.z4j.client.TopicClient;
import lol.pbu.z4j.client.TranslationClient;
import lol.pbu.z4j.client.ViewClient;
import lol.pbu.z4j.model.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Facade preserving backwards compatibility by delegating to domain-specific tool singletons.
 * @deprecated Inject domain-specific tool classes (ZendeskTicketTools, ZendeskSearchTools, etc.) directly.
 */
@Deprecated
@Singleton
public class ZendeskTools {

    private final ZendeskTicketTools ticketTools;
    private final ZendeskSearchTools searchTools;
    private final ZendeskViewTools viewTools;
    private final ZendeskHelpCenterTools helpCenterTools;
    private final ZendeskCommunityTools communityTools;
    private final ZendeskCustomObjectTools customObjectTools;
    private final ZendeskMetadataTools metadataTools;

    @Inject
    public ZendeskTools(
            ZendeskTicketTools ticketTools,
            ZendeskSearchTools searchTools,
            ZendeskViewTools viewTools,
            ZendeskHelpCenterTools helpCenterTools,
            ZendeskCommunityTools communityTools,
            ZendeskCustomObjectTools customObjectTools,
            ZendeskMetadataTools metadataTools
    ) {
        this.ticketTools = ticketTools;
        this.searchTools = searchTools;
        this.viewTools = viewTools;
        this.helpCenterTools = helpCenterTools;
        this.communityTools = communityTools;
        this.customObjectTools = customObjectTools;
        this.metadataTools = metadataTools;
    }

    public ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ViewClient viewClient,
            ArticleClient articleClient,
            CategoryClient categoryClient,
            TranslationClient translationClient,
            TopicClient topicClient,
            PostClient postClient
    ) {
        this(ticketClient, searchClient, ticketFormsClient, customObjectsClient, customObjectRecordsClient, attachmentClient, jobStatusClient, viewClient, articleClient, categoryClient, translationClient, topicClient, postClient, null, null);
    }

    public ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ViewClient viewClient,
            ArticleClient articleClient,
            CategoryClient categoryClient,
            TranslationClient translationClient,
            TopicClient topicClient,
            PostClient postClient,
            @Nullable CustomStatusClient customStatusClient
    ) {
        this(ticketClient, searchClient, ticketFormsClient, customObjectsClient, customObjectRecordsClient, attachmentClient, jobStatusClient, viewClient, articleClient, categoryClient, translationClient, topicClient, postClient, customStatusClient, null);
    }

    public ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ViewClient viewClient,
            ArticleClient articleClient,
            CategoryClient categoryClient,
            TranslationClient translationClient,
            TopicClient topicClient,
            PostClient postClient,
            @Nullable CustomStatusClient customStatusClient,
            @Nullable ZendeskMetadataService metadataService
    ) {
        this(
                ticketClient, searchClient, ticketFormsClient, customObjectsClient, customObjectRecordsClient,
                attachmentClient, jobStatusClient, viewClient, articleClient, categoryClient, translationClient,
                topicClient, postClient, customStatusClient,
                metadataService != null ? metadataService : new ZendeskMetadataService(customStatusClient, ticketFormsClient),
                null
        );
    }

    private ZendeskTools(
            TicketClient ticketClient,
            SearchClient searchClient,
            TicketFormsClient ticketFormsClient,
            CustomObjectsClient customObjectsClient,
            CustomObjectRecordsClient customObjectRecordsClient,
            AttachmentClient attachmentClient,
            JobStatusClient jobStatusClient,
            ViewClient viewClient,
            ArticleClient articleClient,
            CategoryClient categoryClient,
            TranslationClient translationClient,
            TopicClient topicClient,
            PostClient postClient,
            @Nullable CustomStatusClient customStatusClient,
            ZendeskMetadataService effectiveMetadataService,
            Void ignored
    ) {
        this(
                new ZendeskTicketTools(ticketClient, attachmentClient, jobStatusClient, effectiveMetadataService),
                new ZendeskSearchTools(searchClient),
                new ZendeskViewTools(viewClient),
                new ZendeskHelpCenterTools(articleClient, categoryClient, translationClient),
                new ZendeskCommunityTools(topicClient, postClient),
                new ZendeskCustomObjectTools(customObjectsClient, customObjectRecordsClient),
                new ZendeskMetadataTools(effectiveMetadataService, ticketFormsClient, customStatusClient)
        );
    }

    public ZendeskTools(TicketClient ticketClient, SearchClient searchClient) {
        this(
                ticketClient, searchClient, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null
        );
    }

    public ZendeskMetadataService getMetadataService() {
        return ticketTools.getMetadataService();
    }

    // Tickets
    public TicketResponse getTicket(Long ticketId) {
        return ticketTools.getTicket(ticketId);
    }

    public TicketsResponse getTickets(List<Long> ticketIds) {
        return ticketTools.getTickets(ticketIds);
    }

    public TicketsResponse listTickets() {
        return ticketTools.listTickets();
    }

    public TicketCountResponse getTicketCount() {
        return ticketTools.getTicketCount();
    }

    public TicketResponse createTicket(
            String subject,
            @Nullable String comment,
            Boolean isPublic,
            @Nullable String priority,
            @Nullable String status,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        return ticketTools.createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, customStatusId, ticketFormId, request);
    }

    public TicketResponse createTicket(
            String subject,
            @Nullable String comment,
            Boolean isPublic,
            @Nullable String priority,
            @Nullable String status,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            CallToolRequest request
    ) {
        return ticketTools.createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, customStatusId, request);
    }

    public TicketResponse createTicket(
            String subject,
            String comment,
            Boolean isPublic,
            String priority,
            String status,
            List<String> uploadTokens,
            List<String> attachmentFilePaths
    ) {
        return ticketTools.createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths);
    }

    public TicketResponse createTicket(
            String subject,
            String comment,
            Boolean isPublic,
            String priority,
            String status,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type
    ) {
        return ticketTools.createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type);
    }

    public TicketResponse createTicket(
            String subject,
            String comment,
            Boolean isPublic,
            String priority,
            String status,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            CallToolRequest request
    ) {
        return ticketTools.createTicket(subject, comment, isPublic, priority, status, uploadTokens, attachmentFilePaths, customFields, requesterId, type, request);
    }

    public TicketUpdateResponse updateTicket(Long ticketId, TicketMutationOptions options, CallToolRequest request) {
        return ticketTools.updateTicket(ticketId, options, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        return ticketTools.updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, request);
    }

    public TicketUpdateResponse updateTicket(Long ticketId, TicketMutationOptions options) {
        return ticketTools.updateTicket(ticketId, options);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            CallToolRequest request
    ) {
        return ticketTools.updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            CallToolRequest request
    ) {
        return ticketTools.updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            CallToolRequest request
    ) {
        return ticketTools.updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, request);
    }

    public TicketUpdateResponse updateTicket(
            Long ticketId,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields
    ) {
        return ticketTools.updateTicket(ticketId, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields);
    }

    public AttachmentUploadResponse uploadAttachment(String filePath, @Nullable String filename, @Nullable CallToolRequest request) {
        return ticketTools.uploadAttachment(filePath, filename, request);
    }

    public AttachmentUploadResponse uploadAttachment(String filePath, @Nullable String filename) {
        return ticketTools.uploadAttachment(filePath, filename);
    }

    public BatchUpdateResponse batchUpdateTickets(List<Long> ticketIds, TicketMutationOptions options, Boolean async, CallToolRequest request) {
        return ticketTools.batchUpdateTickets(ticketIds, options, async, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Boolean async,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            @Nullable Long ticketFormId,
            CallToolRequest request
    ) {
        return ticketTools.batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, async, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, request);
    }

    public BatchUpdateResponse batchUpdateTickets(List<Long> ticketIds, TicketMutationOptions options) {
        return ticketTools.batchUpdateTickets(ticketIds, options);
    }

    public BatchUpdateResponse batchUpdateTickets(List<Long> ticketIds, TicketMutationOptions options, Boolean async) {
        return ticketTools.batchUpdateTickets(ticketIds, options, async);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            @Nullable String comment,
            @Nullable String status,
            @Nullable String priority,
            @Nullable Boolean isPublic,
            @Nullable List<String> uploadTokens,
            @Nullable List<String> attachmentFilePaths,
            @Nullable Boolean async,
            @Nullable Long problemId,
            @Nullable Boolean convertToIncident,
            @Nullable List<Map<String, Object>> customFields,
            @Nullable Long requesterId,
            @Nullable String type,
            @Nullable Long customStatusId,
            CallToolRequest request
    ) {
        return ticketTools.batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, async, problemId, convertToIncident, customFields, requesterId, type, customStatusId, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean async,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type,
            CallToolRequest request
    ) {
        return ticketTools.batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, async, problemId, convertToIncident, customFields, requesterId, type, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean async,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            Long requesterId,
            String type
    ) {
        return ticketTools.batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, async, problemId, convertToIncident, customFields, requesterId, type);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean async,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields,
            CallToolRequest request
    ) {
        return ticketTools.batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, async, problemId, convertToIncident, customFields, request);
    }

    public BatchUpdateResponse batchUpdateTickets(
            List<Long> ticketIds,
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> uploadTokens,
            List<String> attachmentFilePaths,
            Boolean async,
            Long problemId,
            Boolean convertToIncident,
            List<Map<String, Object>> customFields
    ) {
        return ticketTools.batchUpdateTickets(ticketIds, comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, async, problemId, convertToIncident, customFields);
    }

    public JobStatusResponse getJobStatus(String jobId) {
        return ticketTools.getJobStatus(jobId);
    }

    public TicketFieldsResponse listTicketFields() {
        return ticketTools.listTicketFields();
    }

    public TicketFieldResponse getTicketField(Long fieldId) {
        return ticketTools.getTicketField(fieldId);
    }

    public TicketAuditsResponse getTicketAudits(Long ticketId) {
        return ticketTools.getTicketAudits(ticketId);
    }

    // Search
    public SearchResponse search(String query, @Nullable String include, @Nullable Integer maxResults) {
        return searchTools.search(query, include, maxResults);
    }

    public SearchResponse searchCount(String query) {
        return searchTools.searchCount(query);
    }

    // Views
    public ViewsResponse listViews() {
        return viewTools.listViews();
    }

    public TicketsResponse getViewTickets(Long viewId) {
        return viewTools.getViewTickets(viewId);
    }

    public ViewsResponse listActiveViews() {
        return viewTools.listActiveViews();
    }

    public ViewResponse getView(Long viewId) {
        return viewTools.getView(viewId);
    }

    public ViewExecuteResponse executeView(Long viewId) {
        return viewTools.executeView(viewId);
    }

    public ViewCountResponse getViewTicketCount(Long viewId) {
        return viewTools.getViewTicketCount(viewId);
    }

    // Help Center
    public ArticleResponse getArticle(Long articleId, @Nullable String locale) {
        return helpCenterTools.getArticle(articleId, locale);
    }

    public ArticlesResponse listArticles(
            @Nullable String locale,
            @Nullable String sortBy,
            @Nullable String sortOrder,
            @Nullable Long startTime,
            @Nullable String labelNames
    ) {
        return helpCenterTools.listArticles(locale, sortBy, sortOrder, startTime, labelNames);
    }

    public ArticleResponse createArticle(
            Long sectionId,
            String title,
            String body,
            Long permissionGroupId,
            @Nullable String locale,
            @Nullable Boolean draft,
            @Nullable List<String> labelNames,
            @Nullable Long userSegmentId
    ) {
        return helpCenterTools.createArticle(sectionId, title, body, permissionGroupId, locale, draft, labelNames, userSegmentId);
    }

    public ArticleResponse updateArticle(
            Long articleId,
            @Nullable String title,
            @Nullable String body,
            @Nullable String locale,
            @Nullable Boolean draft,
            @Nullable Long permissionGroupId,
            @Nullable List<String> labelNames,
            @Nullable Long userSegmentId
    ) {
        return helpCenterTools.updateArticle(articleId, title, body, locale, draft, permissionGroupId, labelNames, userSegmentId);
    }

    public Map<String, Object> deleteArticle(Long articleId, @Nullable Boolean confirm, @Nullable String locale) {
        return helpCenterTools.deleteArticle(articleId, confirm, locale);
    }

    public TranslationsResponse listTranslations(String resourceType, Long resourceId) {
        return helpCenterTools.listTranslations(resourceType, resourceId);
    }

    public TranslationResponse getTranslation(String resourceType, Long resourceId, @Nullable String locale) {
        return helpCenterTools.getTranslation(resourceType, resourceId, locale);
    }

    public CategoriesResponse listCategories(@Nullable String locale) {
        return helpCenterTools.listCategories(locale);
    }

    public CategoryResponse getCategory(Long categoryId, @Nullable String locale) {
        return helpCenterTools.getCategory(categoryId, locale);
    }

    // Community
    public TopicsResponse listCommunityTopics() {
        return communityTools.listCommunityTopics();
    }

    public TopicResponse getCommunityTopic(Long topicId) {
        return communityTools.getCommunityTopic(topicId);
    }

    public PostsResponse listCommunityPosts(@Nullable Long topicId) {
        return communityTools.listCommunityPosts(topicId);
    }

    public PostResponse getCommunityPost(Long postId) {
        return communityTools.getCommunityPost(postId);
    }

    public CommunityPostSearchResponse searchCommunityPosts(String query) {
        return communityTools.searchCommunityPosts(query);
    }

    public PostCommentsResponse listCommunityPostComments(Long postId) {
        return communityTools.listCommunityPostComments(postId);
    }

    // Custom Objects
    public CustomObjectsResponse listCustomObjects() {
        return customObjectTools.listCustomObjects();
    }

    public CustomObjectResponse getCustomObject(String customObjectKey) {
        return customObjectTools.getCustomObject(customObjectKey);
    }

    public CustomObjectRecordsResponse listCustomObjectRecords(String customObjectKey) {
        return customObjectTools.listCustomObjectRecords(customObjectKey);
    }

    public CustomObjectRecordResponse getCustomObjectRecord(String customObjectKey, String recordId) {
        return customObjectTools.getCustomObjectRecord(customObjectKey, recordId);
    }

    public CustomObjectRecordsResponse searchCustomObjectRecords(String customObjectKey, String query) {
        return customObjectTools.searchCustomObjectRecords(customObjectKey, query);
    }

    // Metadata
    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Long ticketFormId,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload,
            @Nullable CallToolRequest request
    ) {
        return metadataTools.listCustomStatuses(statusCategory, ticketFormId, includeInactive, fullPayload, request);
    }

    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload,
            @Nullable CallToolRequest request
    ) {
        return metadataTools.listCustomStatuses(statusCategory, includeInactive, fullPayload, request);
    }

    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Long ticketFormId,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload
    ) {
        return metadataTools.listCustomStatuses(statusCategory, ticketFormId, includeInactive, fullPayload);
    }

    public Map<String, Object> listCustomStatuses(
            @Nullable String statusCategory,
            @Nullable Boolean includeInactive,
            @Nullable Boolean fullPayload
    ) {
        return metadataTools.listCustomStatuses(statusCategory, includeInactive, fullPayload);
    }

    public Map<String, Object> listCustomStatuses() {
        return metadataTools.listCustomStatuses();
    }

    public Map<String, Object> listStatusCategories() {
        return metadataTools.listStatusCategories();
    }

    public Map<String, Object> listStatusCategories(@Nullable Long ticketFormId) {
        return metadataTools.listStatusCategories(ticketFormId);
    }

    public Map<String, Object> listTicketForms() {
        return metadataTools.listTicketForms();
    }

    public Map<String, Object> listTicketForms(@Nullable Boolean includeInactive, @Nullable Boolean fullPayload) {
        return metadataTools.listTicketForms(includeInactive, fullPayload);
    }

    public TicketFormResponse getTicketForm(Long ticketFormId) {
        return metadataTools.getTicketForm(ticketFormId);
    }

    public void clearCustomStatusCache() {
        metadataTools.clearCustomStatusCache();
    }

    public void clearTicketFormCache() {
        metadataTools.clearTicketFormCache();
    }

    public void clearAllCaches() {
        metadataTools.clearAllCaches();
    }

    List<TicketFieldCustomStatusObject> getCachedCustomStatuses(boolean forceRefresh) {
        return metadataTools.getCachedCustomStatuses(forceRefresh);
    }

    List<TicketFormStatus> getCachedTicketFormStatuses(boolean forceRefresh) {
        return metadataTools.getCachedTicketFormStatuses(forceRefresh);
    }

    List<TicketForm> getCachedTicketForms(boolean forceRefresh) {
        return metadataTools.getCachedTicketForms(forceRefresh);
    }

    // Package-private helpers for backwards-compatible test validation
    Path validateAndResolveFilePath(String filePath) {
        return ticketTools.validateAndResolveFilePath(filePath);
    }

    String resolveTargetFilename(Path path, @Nullable String filename) {
        return ticketTools.resolveTargetFilename(path, filename);
    }

    void validateFilenameExtension(String filename) {
        ticketTools.validateFilenameExtension(filename);
    }

    String probeContentType(Path path, String targetFilename) {
        return ticketTools.probeContentType(path, targetFilename);
    }

    List<TicketCustomField> parseCustomFields(List<Map<String, Object>> customFields) {
        return ticketTools.parseCustomFields(customFields);
    }

    List<String> resolveUploadTokens(List<String> uploadTokens, List<String> attachmentFilePaths) {
        return ticketTools.resolveUploadTokens(uploadTokens, attachmentFilePaths);
    }

    TicketUpdateInput buildInputFromParams(String comment, String status, String priority, Boolean isPublic, List<String> tokens, List<TicketCustomField> customFields) {
        return ticketTools.buildInputFromParams(comment, status, priority, isPublic, tokens, customFields);
    }

    TicketUpdateInput buildTicketUpdateInput(
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> tokens,
            List<TicketCustomField> customFields,
            Long requesterId,
            String type,
            Boolean convertToIncident,
            Long customStatusId,
            Long ticketFormId
    ) {
        return ticketTools.buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident, customStatusId, ticketFormId);
    }

    TicketUpdateInput buildTicketUpdateInput(
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> tokens,
            List<TicketCustomField> customFields,
            Long requesterId,
            String type,
            Boolean convertToIncident,
            Long customStatusId
    ) {
        return ticketTools.buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident, customStatusId);
    }

    TicketUpdateInput buildTicketUpdateInput(
            String comment,
            String status,
            String priority,
            Boolean isPublic,
            List<String> tokens,
            List<TicketCustomField> customFields,
            Long requesterId,
            String type,
            Boolean convertToIncident
    ) {
        return ticketTools.buildTicketUpdateInput(comment, status, priority, isPublic, tokens, customFields, requesterId, type, convertToIncident);
    }

    String validateResourceType(String resourceType) {
        return helpCenterTools.validateResourceType(resourceType);
    }

    LocaleAbbreviation resolveLocale(@Nullable String locale) {
        return helpCenterTools.resolveLocale(locale);
    }

    SortArticleBy resolveSortArticleBy(@Nullable String sortBy) {
        return helpCenterTools.resolveSortArticleBy(sortBy);
    }

    SortOrder resolveSortOrder(@Nullable String sortOrder) {
        return helpCenterTools.resolveSortOrder(sortOrder);
    }
}
