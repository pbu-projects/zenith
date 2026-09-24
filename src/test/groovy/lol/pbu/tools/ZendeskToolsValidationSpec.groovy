package lol.pbu.tools

import lol.pbu.z4j.client.ArticleClient
import lol.pbu.z4j.client.AttachmentClient
import lol.pbu.z4j.client.CategoryClient
import lol.pbu.z4j.client.CustomObjectRecordsClient
import lol.pbu.z4j.client.CustomObjectsClient
import lol.pbu.z4j.client.JobStatusClient
import lol.pbu.z4j.client.PostClient
import lol.pbu.z4j.client.SearchClient
import lol.pbu.z4j.client.TicketClient
import lol.pbu.z4j.client.TicketFormsClient
import lol.pbu.z4j.client.TopicClient
import lol.pbu.z4j.client.TranslationClient
import lol.pbu.z4j.client.ViewClient
import lol.pbu.z4j.model.LocaleAbbreviation
import lol.pbu.z4j.model.SortArticleBy
import lol.pbu.z4j.model.SortOrder
import spock.lang.Specification

class ZendeskToolsValidationSpec extends Specification {

    TicketClient ticketClient = Mock()
    SearchClient searchClient = Mock()
    TicketFormsClient ticketFormsClient = Mock()
    CustomObjectsClient customObjectsClient = Mock()
    CustomObjectRecordsClient customObjectRecordsClient = Mock()
    AttachmentClient attachmentClient = Mock()
    JobStatusClient jobStatusClient = Mock()
    ViewClient viewClient = Mock()
    ArticleClient articleClient = Mock()
    CategoryClient categoryClient = Mock()
    TranslationClient translationClient = Mock()
    TopicClient topicClient = Mock()
    PostClient postClient = Mock()

    ZendeskTools tools = new ZendeskTools(
            ticketClient, searchClient, ticketFormsClient, customObjectsClient,
            customObjectRecordsClient, attachmentClient, jobStatusClient,
            viewClient, articleClient, categoryClient, translationClient,
            topicClient, postClient
    )

    def "deleteArticle requires articleId and explicit confirmation"() {
        when: "articleId is null"
        tools.deleteArticle(null, true, "en-us")
        then:
        thrown(IllegalArgumentException)

        when: "confirm is null or false"
        tools.deleteArticle(123L, null, "en-us")
        then:
        thrown(IllegalArgumentException)

        when: "confirm is false"
        tools.deleteArticle(123L, false, "en-us")
        then:
        thrown(IllegalArgumentException)
    }

    def "updateArticle requires articleId and at least one non-null update parameter"() {
        when: "articleId is null"
        tools.updateArticle(null, "Title", null, null, null, null, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "all fields are null"
        tools.updateArticle(123L, null, null, null, null, null, null, null)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("At least one field to update")
    }

    def "view tools require viewId"() {
        when:
        tools.getViewTickets(null)
        then:
        thrown(IllegalArgumentException)

        when:
        tools.getView(null)
        then:
        thrown(IllegalArgumentException)

        when:
        tools.executeView(null)
        then:
        thrown(IllegalArgumentException)

        when:
        tools.getViewTicketCount(null)
        then:
        thrown(IllegalArgumentException)
    }

    def "translation tools validate resourceType and resourceId"() {
        when: "resourceType is null or blank"
        tools.listTranslations(null, 1L)
        then:
        thrown(IllegalArgumentException)

        when: "resourceType is invalid"
        tools.listTranslations("invalid_resource", 1L)
        then:
        thrown(IllegalArgumentException)

        when: "resourceId is null"
        tools.listTranslations("articles", null)
        then:
        thrown(IllegalArgumentException)

        when: "getTranslation resourceType is invalid"
        tools.getTranslation("bad_type", 1L, "en-us")
        then:
        thrown(IllegalArgumentException)

        when: "getTranslation resourceId is null"
        tools.getTranslation("articles", null, "en-us")
        then:
        thrown(IllegalArgumentException)
    }

    def "Help Center and Community tools require IDs and non-blank queries"() {
        when:
        tools.getCategory(null, "en-us")
        then:
        thrown(IllegalArgumentException)

        when:
        tools.getCommunityTopic(null)
        then:
        thrown(IllegalArgumentException)

        when:
        tools.getCommunityPost(null)
        then:
        thrown(IllegalArgumentException)

        when:
        tools.searchCommunityPosts(null)
        then:
        thrown(IllegalArgumentException)

        when:
        tools.searchCommunityPosts("   ")
        then:
        thrown(IllegalArgumentException)

        when:
        tools.listCommunityPostComments(null)
        then:
        thrown(IllegalArgumentException)
    }

    def "validates resolution helpers"() {
        expect:
        tools.resolveLocale(null) == LocaleAbbreviation.ENGLISH_UNITED_STATES
        tools.resolveLocale("  ") == LocaleAbbreviation.ENGLISH_UNITED_STATES
        tools.resolveLocale("no") == LocaleAbbreviation.NORWEGIAN
        tools.resolveLocale("es") == LocaleAbbreviation.SPANISH

        tools.resolveSortArticleBy(null) == null
        tools.resolveSortArticleBy("  ") == null
        tools.resolveSortArticleBy("title") == SortArticleBy.TITLE
        tools.resolveSortArticleBy("created_at") == SortArticleBy.CREATED_AT

        tools.resolveSortOrder(null) == null
        tools.resolveSortOrder("  ") == null
        tools.resolveSortOrder("ascending") == SortOrder.ASCENDING
        tools.resolveSortOrder("descending") == SortOrder.DESCENDING
        tools.resolveSortOrder("asc") == SortOrder.ASCENDING
        tools.resolveSortOrder("desc") == SortOrder.DESCENDING

        tools.validateResourceType("article") == "articles"
        tools.validateResourceType("section") == "sections"
        tools.validateResourceType("category") == "categories"
        tools.validateResourceType("articles") == "articles"

        when:
        tools.resolveLocale("invalid-locale-xyz")
        then:
        thrown(IllegalArgumentException)

        when:
        tools.resolveSortArticleBy("invalid-sort")
        then:
        thrown(IllegalArgumentException)

        when:
        tools.resolveSortOrder("invalid-order")
        then:
        thrown(IllegalArgumentException)
    }

    def "successfully executes Help Center, Translations, and Community tools happy paths"() {
        given:
        articleClient.createArticle(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ArticleResponse())
        articleClient.updateArticle(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ArticleResponse())
        articleClient.deleteArticle(*_) >> reactor.core.publisher.Mono.empty()
        translationClient.listTranslations(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.TranslationsResponse())
        translationClient.showTranslation(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.TranslationResponse())
        categoryClient.listCategories(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.CategoriesResponse())
        categoryClient.listCategoriesNoLocale(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.CategoriesResponse())
        categoryClient.showCategory(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.CategoryResponse())
        categoryClient.showCategoryNoLocale(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.CategoryResponse())
        topicClient.showTopic(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.TopicResponse())
        topicClient.listTopics() >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.TopicsResponse())
        postClient.showPost(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.PostResponse())
        postClient.listPosts() >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.PostsResponse())
        postClient.listPostsByTopic(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.PostsResponse())
        postClient.searchPosts(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.CommunityPostSearchResponse())
        postClient.listPostComments(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.PostCommentsResponse())

        expect:
        tools.createArticle(1L, "Title", "<p>Body</p>", 2L, "en-us", false, ["label1"], 3L) != null
        tools.updateArticle(1L, "New Title", null, "en-us", null, null, null, null) != null
        tools.deleteArticle(1L, true, "en-us") == [success: true, deletedArticleId: 1L]
        tools.listTranslations("articles", 1L) != null
        tools.getTranslation("articles", 1L, "en-us") != null
        tools.listCategories("en-us") != null
        tools.listCategories(null) != null
        tools.getCategory(1L, "en-us") != null
        tools.getCategory(1L, null) != null
        tools.listCommunityTopics() != null
        tools.getCommunityTopic(1L) != null
        tools.listCommunityPosts(1L) != null
        tools.listCommunityPosts(null) != null
        tools.getCommunityPost(1L) != null
        tools.searchCommunityPosts("query") != null
        tools.listCommunityPostComments(1L) != null
    }
}
