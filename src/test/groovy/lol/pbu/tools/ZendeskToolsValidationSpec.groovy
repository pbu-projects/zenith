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

    def "createArticle and getArticle require valid parameters"() {
        when: "sectionId is null"
        tools.createArticle(null, "Title", "Body", 1L, "en-us", false, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "title is null"
        tools.createArticle(1L, null, "Body", 1L, "en-us", false, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "title is blank"
        tools.createArticle(1L, "   ", "Body", 1L, "en-us", false, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "body is null"
        tools.createArticle(1L, "Title", null, 1L, "en-us", false, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "body is blank"
        tools.createArticle(1L, "Title", "   ", 1L, "en-us", false, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "permissionGroupId is null"
        tools.createArticle(1L, "Title", "Body", null, "en-us", false, null, null)
        then:
        thrown(IllegalArgumentException)

        when: "articleId is null"
        tools.getArticle(null, "en-us")
        then:
        thrown(IllegalArgumentException)
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
        tools.validateResourceType("sections") == "sections"
        tools.validateResourceType("categories") == "categories"

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

    def "successfully executes Help Center, Views, Translations, and Community tools happy paths"() {
        given:
        articleClient.createArticle(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ArticleResponse())
        articleClient.updateArticle(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ArticleResponse())
        articleClient.deleteArticle(*_) >> reactor.core.publisher.Mono.empty()
        articleClient.listArticles(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ArticlesResponse())
        articleClient.showArticle(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ArticleResponse())
        viewClient.listViews() >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ViewsResponse())
        viewClient.listActiveViews() >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ViewsResponse())
        viewClient.listTicketsForView(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.TicketsResponse())
        viewClient.showView(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ViewResponse())
        viewClient.executeView(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ViewExecuteResponse())
        viewClient.countView(*_) >> reactor.core.publisher.Mono.just(new lol.pbu.z4j.model.ViewCountResponse())
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
        tools.createArticle(1L, "Draft Article", "<p>Body</p>", 2L, "en-us", null, null, null) != null
        tools.updateArticle(1L, "New Title", null, "en-us", null, null, null, null) != null
        tools.updateArticle(1L, "New Title", "New Body", "en-us", true, 2L, ["label1"], 5L) != null
        tools.deleteArticle(1L, true, "en-us") == [success: true, deletedArticleId: 1L]
        tools.listArticles("en-us", "title", "asc", 1000L, "label1,label2") != null
        tools.listArticles(null, null, null, null, null) != null
        tools.getArticle(1L, "en-us") != null
        tools.getArticle(1L, null) != null
        tools.listViews() != null
        tools.listActiveViews() != null
        tools.getView(1L) != null
        tools.getViewTickets(1L) != null
        tools.executeView(1L) != null
        tools.getViewTicketCount(1L) != null
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

    def "listTicketForms handles full payload, summary mode, active filtering, and empty responses"() {
        given:
        def form1 = new lol.pbu.z4j.model.TicketForm().tap {
            id = 1L
            name = "Form 1"
            displayName = "Display 1"
            active = true
            defaultForm = true
        }
        def form2 = new lol.pbu.z4j.model.TicketForm().tap {
            id = 2L
            name = "Form 2"
            displayName = "Display 2"
            active = false
            defaultForm = false
        }
        def responseWithForms = new lol.pbu.z4j.model.TicketFormsResponse().tap {
            ticketForms = [form1, form2]
        }
        def emptyResponse = new lol.pbu.z4j.model.TicketFormsResponse().tap {
            ticketForms = null
        }

        when: "response is null or empty"
        ticketFormsClient.listTicketForms() >>> [
                reactor.core.publisher.Mono.empty(),
                reactor.core.publisher.Mono.just(emptyResponse),
                reactor.core.publisher.Mono.just(responseWithForms),
                reactor.core.publisher.Mono.just(responseWithForms),
                reactor.core.publisher.Mono.just(responseWithForms)
        ]

        def rNull = tools.listTicketForms()
        def rEmpty = tools.listTicketForms(null, null)
        def rSummaryActiveOnly = tools.listTicketForms(false, false)
        def rSummaryAll = tools.listTicketForms(true, false)
        def rFullAll = tools.listTicketForms(true, true)

        then:
        rNull.ticket_forms == []
        rEmpty.ticket_forms == []
        rSummaryActiveOnly.ticket_forms.size() == 1
        rSummaryActiveOnly.ticket_forms[0].id == 1L
        rSummaryAll.ticket_forms.size() == 2
        rFullAll.ticket_forms.size() == 2
        rFullAll.ticket_forms[0] instanceof lol.pbu.z4j.model.TicketForm
    }

    def "parseCustomFields handles various inputs and validates custom fields"() {
        when:
        def resNull = tools.parseCustomFields(null)
        def resEmpty = tools.parseCustomFields([])
        def resValid = tools.parseCustomFields([
                [id: 100L, value: "val1"],
                [id: "200", value: "val2"]
        ])

        then:
        resNull.isEmpty()
        resEmpty.isEmpty()
        resValid.size() == 2
        resValid[0].id == 100L
        resValid[1].id == 200L

        when: "custom field missing id"
        tools.parseCustomFields([[value: "no-id"]])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Custom field must have an 'id'")
    }
}
