package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.z4j.client.ArticleClient;
import lol.pbu.z4j.client.CategoryClient;
import lol.pbu.z4j.client.TranslationClient;
import lol.pbu.z4j.model.Article;
import lol.pbu.z4j.model.ArticleCreateRequest;
import lol.pbu.z4j.model.ArticleResponse;
import lol.pbu.z4j.model.ArticlesResponse;
import lol.pbu.z4j.model.ArticleUpdateRequest;
import lol.pbu.z4j.model.CategoriesResponse;
import lol.pbu.z4j.model.CategoryResponse;
import lol.pbu.z4j.model.LocaleAbbreviation;
import lol.pbu.z4j.model.SortArticleBy;
import lol.pbu.z4j.model.SortOrder;
import lol.pbu.z4j.model.TranslationResponse;
import lol.pbu.z4j.model.TranslationsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class ZendeskHelpCenterTools {

    private static final Logger log = LoggerFactory.getLogger(ZendeskHelpCenterTools.class);
    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of("articles", "sections", "categories");

    private final ArticleClient articleClient;
    private final CategoryClient categoryClient;
    private final TranslationClient translationClient;

    @Inject
    public ZendeskHelpCenterTools(ArticleClient articleClient, CategoryClient categoryClient, TranslationClient translationClient) {
        this.articleClient = articleClient;
        this.categoryClient = categoryClient;
        this.translationClient = translationClient;
    }
    @Tool(description = "Get details and content of a specific Zendesk Help Center / Knowledge Base article by its numeric ID")
    public ArticleResponse getArticle(
            @ToolArg(description = "The numeric article ID") Long articleId,
            @ToolArg(description = "Optional locale code, e.g. 'en-us'. Defaults to 'en-us'") @Nullable String locale
    ) {
        log.info("MCP Tool called: getArticle(id={}, locale='{}')", articleId, locale);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        return articleClient.showArticle(localeAbbr, articleId).block();
    }

    @Tool(description = "List Zendesk Help Center / Knowledge Base articles")
    public ArticlesResponse listArticles(
            @ToolArg(description = "Optional locale code, e.g. 'en-us'. Defaults to 'en-us'") @Nullable String locale,
            @ToolArg(description = "Optional sort field: position, title, created_at, updated_at, edited_at") @Nullable String sortBy,
            @ToolArg(description = "Optional sort order: asc or desc") @Nullable String sortOrder,
            @ToolArg(description = "Optional start time Unix epoch timestamp for incremental listing") @Nullable Long startTime,
            @ToolArg(description = "Optional comma-delimited label names") @Nullable String labelNames
    ) {
        log.info("MCP Tool called: listArticles(locale='{}', sortBy='{}')", locale, sortBy);
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        return articleClient.listArticles(
                localeAbbr,
                resolveSortArticleBy(sortBy),
                resolveSortOrder(sortOrder),
                startTime,
                labelNames
        ).block();
    }

    @Tool(description = "Create a new article in a Zendesk Help Center section")
    public ArticleResponse createArticle(
            @ToolArg(description = "The ID of the section to which the article belongs") Long sectionId,
            @ToolArg(description = "The title of the article") String title,
            @ToolArg(description = "The HTML body content of the article") String body,
            @ToolArg(description = "The ID of the permission group defining who can edit/publish this article") Long permissionGroupId,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale,
            @ToolArg(description = "Optional flag indicating whether this article is a draft. Defaults to true for safety") @Nullable Boolean draft,
            @ToolArg(description = "Optional list of label names for the article") @Nullable List<String> labelNames,
            @ToolArg(description = "Optional user segment ID defining who can view this article") @Nullable Long userSegmentId
    ) {
        log.info("MCP Tool called: createArticle(sectionId={}, title='{}')", sectionId, title);
        if (sectionId == null) {
            throw new IllegalArgumentException("sectionId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        if (permissionGroupId == null) {
            throw new IllegalArgumentException("permissionGroupId is required");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        boolean isDraft = draft == null || draft;
        Article article = new Article()
                .setTitle(title)
                .setBody(body)
                .setPermissionGroupId(permissionGroupId)
                .setLocaleAbbreviation(localeAbbr)
                .setDraft(isDraft);
        if (labelNames != null && !labelNames.isEmpty()) {
            article.setLabelNames(labelNames);
        }
        if (userSegmentId != null) {
            article.setUserSegmentId(userSegmentId);
        }
        return articleClient.createArticle(localeAbbr, sectionId, new ArticleCreateRequest(article)).block();
    }

    @Tool(description = "Update an existing Zendesk Help Center article")
    public ArticleResponse updateArticle(
            @ToolArg(description = "The unique numeric ID of the article") Long articleId,
            @ToolArg(description = "Optional new title of the article") @Nullable String title,
            @ToolArg(description = "Optional new HTML body content of the article") @Nullable String body,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale,
            @ToolArg(description = "Optional flag to publish (false) or draft (true) the article") @Nullable Boolean draft,
            @ToolArg(description = "Optional permission group ID defining who can edit/publish this article") @Nullable Long permissionGroupId,
            @ToolArg(description = "Optional list of label names for the article") @Nullable List<String> labelNames,
            @ToolArg(description = "Optional user segment ID defining who can view this article") @Nullable Long userSegmentId
    ) {
        log.info("MCP Tool called: updateArticle(articleId={})", articleId);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        if (title == null && body == null && labelNames == null && userSegmentId == null && draft == null && permissionGroupId == null) {
            throw new IllegalArgumentException("At least one field to update (title, body, labelNames, userSegmentId, draft, permissionGroupId) must be provided.");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        Article article = new Article();
        if (title != null) {
            article.setTitle(title);
        }
        if (body != null) {
            article.setBody(body);
        }
        if (draft != null) {
            article.setDraft(draft);
        }
        if (permissionGroupId != null) {
            article.setPermissionGroupId(permissionGroupId);
        }
        if (labelNames != null) {
            article.setLabelNames(labelNames);
        }
        if (userSegmentId != null) {
            article.setUserSegmentId(userSegmentId);
        }
        return articleClient.updateArticle(localeAbbr, articleId, new ArticleUpdateRequest(article)).block();
    }

    @Tool(description = "Delete a Zendesk Help Center article translation for a given locale (or the article if it is the only translation). Requires confirm=true to prevent accidental deletion")
    public Map<String, Object> deleteArticle(
            @ToolArg(description = "The unique numeric ID of the article to delete") Long articleId,
            @ToolArg(description = "Must be explicitly set to true to confirm deletion of this article") @Nullable Boolean confirm,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale
    ) {
        log.info("MCP Tool called: deleteArticle(articleId={}, confirm={})", articleId, confirm);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        if (!Boolean.TRUE.equals(confirm)) {
            throw new IllegalArgumentException("Deletion requires explicit confirmation. Set 'confirm' to true to proceed.");
        }
        LocaleAbbreviation localeAbbr = resolveLocale(locale);
        articleClient.deleteArticle(localeAbbr, articleId).block();
        return Map.of("success", true, "deletedArticleId", articleId);
    }

    @Tool(description = "List translations for a Zendesk Help Center resource (e.g. 'articles', 'sections', 'categories')")
    public TranslationsResponse listTranslations(
            @ToolArg(description = "The resource type: 'articles', 'sections', or 'categories'") String resourceType,
            @ToolArg(description = "The numeric ID of the parent resource") Long resourceId
    ) {
        log.info("MCP Tool called: listTranslations(resourceType='{}', resourceId={})", resourceType, resourceId);
        String validResourceType = validateResourceType(resourceType);
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId is required");
        }
        return translationClient.listTranslations(validResourceType, resourceId).block();
    }

    @Tool(description = "Get a specific translation for a Zendesk Help Center resource by locale")
    public TranslationResponse getTranslation(
            @ToolArg(description = "The resource type: 'articles', 'sections', or 'categories'") String resourceType,
            @ToolArg(description = "The numeric ID of the parent resource") Long resourceId,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). Defaults to 'en-us'") @Nullable String locale
    ) {
        log.info("MCP Tool called: getTranslation(resourceType='{}', resourceId={}, locale='{}')", resourceType, resourceId, locale);
        String validResourceType = validateResourceType(resourceType);
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId is required");
        }
        return translationClient.showTranslation(validResourceType, resourceId, resolveLocale(locale)).block();
    }

    @Tool(description = "List Zendesk Help Center categories")
    public CategoriesResponse listCategories(
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us'). If omitted, lists categories across all locales") @Nullable String locale
    ) {
        log.info("MCP Tool called: listCategories(locale='{}')", locale);
        if (locale != null && !locale.isBlank()) {
            return categoryClient.listCategories(resolveLocale(locale), null, null).block();
        }
        return categoryClient.listCategoriesNoLocale(null, null).block();
    }

    @Tool(description = "Get details of a specific Zendesk Help Center category by its numeric ID")
    public CategoryResponse getCategory(
            @ToolArg(description = "The numeric category ID") Long categoryId,
            @ToolArg(description = "Optional locale abbreviation (e.g. 'en-us')") @Nullable String locale
    ) {
        log.info("MCP Tool called: getCategory(categoryId={}, locale='{}')", categoryId, locale);
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId is required");
        }
        if (locale != null && !locale.isBlank()) {
            return categoryClient.showCategory(resolveLocale(locale), categoryId).block();
        }
        return categoryClient.showCategoryNoLocale(categoryId).block();
    }


    String validateResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType is required. Allowed values are: " + ALLOWED_RESOURCE_TYPES);
        }
        String normalized = resourceType.trim().toLowerCase();
        if ("article".equals(normalized)) {
            normalized = "articles";
        } else if ("section".equals(normalized)) {
            normalized = "sections";
        } else if ("category".equals(normalized)) {
            normalized = "categories";
        }
        if (!ALLOWED_RESOURCE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid resourceType: '" + resourceType + "'. Allowed values are: " + ALLOWED_RESOURCE_TYPES);
        }
        return normalized;
    }

    LocaleAbbreviation resolveLocale(@Nullable String locale) {
        if (locale == null || locale.isBlank()) {
            return LocaleAbbreviation.ENGLISH_UNITED_STATES;
        }
        String cleanLocale = locale.trim().toLowerCase();
        if ("no".equals(cleanLocale)) {
            return LocaleAbbreviation.NORWEGIAN;
        }
        try {
            return LocaleAbbreviation.fromValue(cleanLocale);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid locale: '" + locale + "'. Expected a standard locale code such as 'en-us', 'es', 'fr', 'de', 'ja', etc.");
        }
    }

    SortArticleBy resolveSortArticleBy(@Nullable String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }
        String cleanSort = sortBy.trim().toLowerCase();
        try {
            return SortArticleBy.fromValue(cleanSort);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid sortBy: '" + sortBy + "'. Allowed values are: position, title, created_at, updated_at, edited_at");
        }
    }

    SortOrder resolveSortOrder(@Nullable String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) {
            return null;
        }
        String cleanOrder = sortOrder.trim().toLowerCase();
        if ("ascending".equals(cleanOrder)) {
            cleanOrder = "asc";
        } else if ("descending".equals(cleanOrder)) {
            cleanOrder = "desc";
        }
        try {
            return SortOrder.fromValue(cleanOrder);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid sortOrder: '" + sortOrder + "'. Allowed values are: 'asc' or 'desc'");
        }
    }


}
