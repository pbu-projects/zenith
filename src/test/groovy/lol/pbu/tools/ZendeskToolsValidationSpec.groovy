package lol.pbu.tools

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
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
import lol.pbu.z4j.model.JobStatus
import lol.pbu.z4j.model.JobStatusResponse
import lol.pbu.z4j.model.LocaleAbbreviation
import lol.pbu.z4j.model.SortArticleBy
import lol.pbu.z4j.model.SortOrder
import lol.pbu.z4j.model.Ticket
import lol.pbu.z4j.model.TicketCreateRequest
import lol.pbu.z4j.model.TicketResponse
import lol.pbu.z4j.model.TicketType
import lol.pbu.z4j.model.TicketUpdateInputType
import lol.pbu.z4j.model.TicketUpdateRequest
import lol.pbu.z4j.model.TicketUpdateResponse
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

    def "createTicket correctly parses and applies customFields to TicketCreateInput"() {
        given:
        TicketCreateRequest capturedReq = null
        ticketClient.createTicket(_ as TicketCreateRequest) >> { TicketCreateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketResponse())
        }

        def customFieldsInput = [
                [id: 12345L, value: "custom-value-1"],
                [id: "67890", value: "custom-value-2"]
        ]
        def request = new CallToolRequest("createTicket", [
                subject: "Test Subject",
                comment: "Test Comment",
                isPublic: true,
                customFields: customFieldsInput
        ])

        when:
        def resp = tools.createTicket(
                "Test Subject", "Test Comment", true, null, null, null, null,
                customFieldsInput, null, null, request
        )

        then:
        resp != null
        capturedReq != null
        capturedReq.ticket != null
        capturedReq.ticket.customFields != null
        capturedReq.ticket.customFields.size() == 2
        capturedReq.ticket.customFields[0].id == 12345L
        capturedReq.ticket.customFields[0].value == "custom-value-1"
        capturedReq.ticket.customFields[1].id == 67890L
        capturedReq.ticket.customFields[1].value == "custom-value-2"
    }

    def "createTicket with invalid custom fields (missing id) throws IllegalArgumentException"() {
        when:
        tools.createTicket(
                "Test Subject", "Test Comment", true, null, null, null, null,
                [[value: "missing-id"]], null, null, null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Custom field must have an 'id'")
    }

    def "createTicket fails on unrecognized parameters in CallToolRequest"() {
        given:
        def request = new CallToolRequest("createTicket", [
                subject: "Test Subject",
                comment: "Test Comment",
                isPublic: true,
                unrecognizedParam: "bad-data"
        ])

        when:
        tools.createTicket(
                "Test Subject", "Test Comment", true, null, null, null, null,
                null, null, null, request
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unrecognized parameter")
        e.message.contains("unrecognizedParam")
    }

    def "createTicket accepts description in request arguments aliased to comment when comment is null"() {
        given:
        TicketCreateRequest capturedReq = null
        ticketClient.createTicket(_ as TicketCreateRequest) >> { TicketCreateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketResponse())
        }
        def request = new CallToolRequest("createTicket", [
                subject: "Test Subject",
                description: "Initial description as comment",
                isPublic: true
        ])

        when:
        def resp = tools.createTicket(
                "Test Subject", null, true, null, null, null, null,
                null, null, null, request
        )

        then:
        resp != null
        capturedReq != null
        capturedReq.ticket != null
        capturedReq.ticket.comment != null
        capturedReq.ticket.comment.body == "Initial description as comment"
    }

    def "createTicket sets requesterId on TicketCreateInput"() {
        given:
        TicketCreateRequest capturedReq = null
        ticketClient.createTicket(_ as TicketCreateRequest) >> { TicketCreateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketResponse())
        }

        when:
        tools.createTicket(
                "Test Subject", "Test Comment", true, null, null, null, null,
                null, 99999L, null, null
        )

        then:
        capturedReq != null
        capturedReq.ticket != null
        (capturedReq.ticket.requesterId as Long) == 99999L
    }

    def "createTicket sets type on TicketCreateInput"() {
        given:
        TicketCreateRequest capturedReq = null
        ticketClient.createTicket(_ as TicketCreateRequest) >> { TicketCreateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketResponse())
        }

        when:
        tools.createTicket(
                "Test Subject", "Test Comment", true, null, null, null, null,
                null, null, typeStr, null
        )

        then:
        capturedReq != null
        capturedReq.ticket != null
        capturedReq.ticket.type == expectedType

        where:
        typeStr     | expectedType
        "problem"   | TicketUpdateInputType.PROBLEM
        "incident"  | TicketUpdateInputType.INCIDENT
        "question"  | TicketUpdateInputType.QUESTION
        "task"      | TicketUpdateInputType.TASK
        "none"      | null
        ""          | null
    }

    def "createTicket with invalid type throws IllegalArgumentException"() {
        when:
        tools.createTicket(
                "Test Subject", "Test Comment", true, null, null, null, null,
                null, null, "invalid_type", null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.toLowerCase().contains("problem")
        e.message.toLowerCase().contains("incident")
        e.message.toLowerCase().contains("question")
        e.message.toLowerCase().contains("task")
    }

    def "updateTicket and batchUpdateTickets reject description parameter"() {
        when: "description is passed to updateTicket in CallToolRequest"
        def updateReq = new CallToolRequest("updateTicket", [
                ticketId: 100L,
                description: "Cannot edit description"
        ])
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, updateReq)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.toLowerCase().contains("description")
        e1.message.toLowerCase().contains("read-only") || e1.message.toLowerCase().contains("read only")
        e1.message.contains("comment")

        when: "description is passed to batchUpdateTickets in CallToolRequest"
        def batchReq = new CallToolRequest("batchUpdateTickets", [
                ticketIds: [100L, 101L],
                description: "Cannot edit description"
        ])
        tools.batchUpdateTickets([100L, 101L], null, null, null, null, null, null, false, null, null, null, null, null, batchReq)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.toLowerCase().contains("description")
        e2.message.toLowerCase().contains("read-only") || e2.message.toLowerCase().contains("read only")
        e2.message.contains("comment")
    }

    def "updateTicket sets requesterId on TicketUpdateInput"() {
        given:
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }

        when:
        tools.updateTicket(
                100L, null, null, null, null, null, null,
                null, null, null, 88888L, null, null
        )

        then:
        capturedReq != null
        capturedReq.ticket != null
        (capturedReq.ticket.requesterId as Long) == 88888L
    }

    def "updateTicket sets type on TicketUpdateInput"() {
        given:
        TicketUpdateRequest capturedReq = null
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(100L).tap {
                type = TicketType.TASK
                hasIncidents = false
            }
        })
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }

        when:
        tools.updateTicket(
                100L, null, null, null, null, null, null,
                null, null, null, null, typeStr, null
        )

        then:
        capturedReq != null
        capturedReq.ticket != null
        capturedReq.ticket.type == expectedType

        where:
        typeStr     | expectedType
        "problem"   | TicketUpdateInputType.PROBLEM
        "incident"  | TicketUpdateInputType.INCIDENT
        "question"  | TicketUpdateInputType.QUESTION
        "task"      | TicketUpdateInputType.TASK
        "none"      | null
        ""          | null
    }

    def "updateTicket with invalid type throws IllegalArgumentException"() {
        when:
        tools.updateTicket(
                100L, null, null, null, null, null, null,
                null, null, null, null, "invalid_type", null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.toLowerCase().contains("problem")
        e.message.toLowerCase().contains("incident")
        e.message.toLowerCase().contains("question")
        e.message.toLowerCase().contains("task")
    }

    def "updateTicket rejects type conversion on a problem parent with linked incidents"() {
        given:
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(100L).tap {
                id = 100L
                type = TicketType.PROBLEM
                hasIncidents = true
            }
        })

        when: "attempting to change type away from problem"
        tools.updateTicket(
                100L, null, null, null, null, null, null,
                null, null, null, null, targetType, null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("ticket #100 is a parent problem with linked incidents") || e.message.contains("reassign or resolve those first")

        where:
        targetType << ["incident", "question", "task", "none", ""]
    }

    def "batchUpdateTickets sets requesterId and type on TicketUpdateInput for concurrent updates"() {
        given:
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketUpdateResponse().tap {
                ticket = new Ticket(100L)
            })
        }

        when:
        def resp = tools.batchUpdateTickets(
                [100L], null, null, null, null, null, null,
                false, null, null, null, 77777L, "task", null
        )

        then:
        resp != null
        resp.results() != null
        resp.results().size() == 1
        resp.results()[0].success()
        capturedReq != null
        capturedReq.ticket != null
        (capturedReq.ticket.requesterId as Long) == 77777L
        capturedReq.ticket.type == TicketUpdateInputType.TASK
    }

    def "batchUpdateTickets sets requesterId and type on TicketUpdateInput for async bulk updates"() {
        given:
        TicketUpdateRequest capturedReq = null
        ticketClient.updateManyTickets("100,101", _ as TicketUpdateRequest) >> { String ids, TicketUpdateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new JobStatusResponse().tap {
                jobStatus = new JobStatus().tap { id = "job-456" }
            })
        }

        when:
        def resp = tools.batchUpdateTickets(
                [100L, 101L], null, null, null, null, null, null,
                true, null, null, null, 77777L, "task", null
        )

        then:
        resp != null
        resp.jobStatus() != null
        resp.jobStatus().id == "job-456"
        capturedReq != null
        capturedReq.ticket != null
        (capturedReq.ticket.requesterId as Long) == 77777L
        capturedReq.ticket.type == TicketUpdateInputType.TASK
    }

    def "batchUpdateTickets with invalid type throws IllegalArgumentException"() {
        when:
        tools.batchUpdateTickets(
                [100L], null, null, null, null, null, null,
                false, null, null, null, null, "invalid_type", null
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.toLowerCase().contains("problem")
        e.message.toLowerCase().contains("incident")
        e.message.toLowerCase().contains("question")
        e.message.toLowerCase().contains("task")
    }

    def "requesterId exceeding 32-bit integer range throws IllegalArgumentException across tools"() {
        when: "createTicket with 64-bit requesterId"
        tools.createTicket("Subject", "Comment", true, null, null, null, null, null, 382716491823L, null, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("exceeds 32-bit integer range")

        when: "updateTicket with 64-bit requesterId"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, 382716491823L, null, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("exceeds 32-bit integer range")

        when: "batchUpdateTickets with 64-bit requesterId (concurrent)"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, null, null, null, 382716491823L, null, null)

        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message.contains("exceeds 32-bit integer range")

        when: "batchUpdateTickets with 64-bit requesterId (asyncBulk)"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, true, null, null, null, 382716491823L, null, null)

        then:
        def e4 = thrown(IllegalArgumentException)
        e4.message.contains("exceeds 32-bit integer range")
    }

    def "createTicket validates comment and description parameters"() {
        when: "both comment and description are provided with conflicting text"
        def reqBoth = new CallToolRequest("createTicket", [
                subject: "Subject",
                comment: "Comment A",
                description: "Comment B",
                isPublic: true
        ])
        tools.createTicket("Subject", "Comment A", true, null, null, null, null, null, null, null, reqBoth)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("Provide either 'comment' or 'description'")

        when: "neither comment nor description is provided"
        tools.createTicket("Subject", null, true, null, null, null, null, null, null, null, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("Either 'comment' or 'description' is required")
    }

    def "parseCustomFields handles null elements and non-numeric IDs gracefully"() {
        when: "list contains a null element"
        def res = tools.parseCustomFields([null, [id: 123L, value: "val"]])

        then:
        res.size() == 1
        res[0].id == 123L

        when: "id is non-numeric string"
        tools.parseCustomFields([[id: "not-a-number", value: "val"]])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Custom field 'id' must be a numeric ID")
    }

    def "batchUpdateTickets rejects type conversion on a problem parent with linked incidents"() {
        given:
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(100L).tap {
                id = 100L
                type = TicketType.PROBLEM
                hasIncidents = true
            }
        })

        when: "concurrent batch update attempts to change type away from problem"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, null, null, null, null, "task", null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("ticket #100 is a parent problem with linked incidents")

        when: "asyncBulk batch update attempts to change type away from problem"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, true, null, null, null, null, "task", null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("ticket #100 is a parent problem with linked incidents")
    }

    def "overloaded tool methods execute and delegate correctly"() {
        given:
        ticketClient.createTicket(_ as TicketCreateRequest) >> reactor.core.publisher.Mono.just(new TicketResponse())
        ticketClient.updateTicket(_ as Long, _ as TicketUpdateRequest) >> reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        ticketClient.updateManyTickets(_ as String, _ as TicketUpdateRequest) >> reactor.core.publisher.Mono.just(new JobStatusResponse().tap {
            jobStatus = new JobStatus().tap { id = "job-1" }
        })

        when: "calling createTicket 7-arg overload"
        def r1 = tools.createTicket("Subj", "Comment", true, "low", "open", null, null)

        then:
        r1 != null

        when: "calling createTicket 10-arg overload"
        def r2 = tools.createTicket("Subj", "Comment", true, "low", "open", null, null, null, 123L, "task")

        then:
        r2 != null

        when: "calling updateTicket 11-arg overload"
        def r3 = tools.updateTicket(100L, "Comment", "open", "low", true, null, null, null, null, null, null)

        then:
        r3 != null

        when: "calling batchUpdateTickets 12-arg overload"
        def r4 = tools.batchUpdateTickets([100L], "Comment", "open", "low", true, null, null, true, null, null, null, null)

        then:
        r4 != null
        r4.jobStatus().id == "job-1"

        when: "calling batchUpdateTickets 11-arg overload"
        def r5 = tools.batchUpdateTickets([100L], "Comment", "open", "low", true, null, null, true, null, null, null)

        then:
        r5 != null
        r5.jobStatus().id == "job-1"
    }

    def "batchUpdateTickets supports problemId linking in both asyncBulk and concurrent modes"() {
        given:
        TicketUpdateRequest capturedAsyncReq = null
        TicketUpdateRequest capturedConcReq = null
        ticketClient.showTicket(200L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(200L).tap {
                id = 200L
                type = TicketType.PROBLEM
            }
        })
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(100L).tap {
                id = 100L
                type = TicketType.INCIDENT
            }
        })
        ticketClient.updateManyTickets("100", _ as TicketUpdateRequest) >> { String ids, TicketUpdateRequest req ->
            capturedAsyncReq = req
            reactor.core.publisher.Mono.just(new JobStatusResponse().tap {
                jobStatus = new JobStatus().tap { id = "job-p1" }
            })
        }
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedConcReq = req
            reactor.core.publisher.Mono.just(new TicketUpdateResponse().tap {
                ticket = new Ticket(100L)
            })
        }

        when: "linking problem in asyncBulk mode"
        def rAsync = tools.batchUpdateTickets([100L], null, null, null, null, null, null, true, 200L, null, null, null, null, null)

        then:
        rAsync.jobStatus() != null
        capturedAsyncReq != null
        capturedAsyncReq.ticket != null
        capturedAsyncReq.ticket.problemId == 200
        capturedAsyncReq.ticket.type == TicketUpdateInputType.INCIDENT

        when: "linking problem in concurrent mode"
        def rConc = tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, 200L, null, null, null, null, null)

        then:
        rConc.results() != null
        rConc.results()[0].success()
        capturedConcReq != null
        capturedConcReq.ticket != null
        capturedConcReq.ticket.problemId == 200
        capturedConcReq.ticket.type == TicketUpdateInputType.INCIDENT
    }

    def "batchUpdateTickets handles convertToIncident fallback and update failures in concurrent mode"() {
        given:
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            reactor.core.publisher.Mono.just(new TicketUpdateResponse().tap {
                ticket = new Ticket(100L)
            })
        }
        ticketClient.updateTicket(101L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            reactor.core.publisher.Mono.error(new RuntimeException("Simulated API failure"))
        }

        when: "updating with convertToIncident=true and type=null, with one ticket failing"
        def resp = tools.batchUpdateTickets([100L, 101L], null, null, null, null, null, null, false, null, true, null, null, null, null)

        then:
        resp.results().size() == 2
        resp.results()[0].success()
        capturedReq.ticket.type == TicketUpdateInputType.INCIDENT
        !resp.results()[1].success()
        resp.results()[1].error().contains("Simulated API failure")
    }

    def "validateProblemTarget throws on non-problem or retrieval failure"() {
        given:
        ticketClient.showTicket(300L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(300L).tap {
                id = 300L
                type = TicketType.TASK
            }
        })
        ticketClient.showTicket(400L) >> reactor.core.publisher.Mono.error(new RuntimeException("Not found"))

        when: "target is not a problem ticket"
        tools.updateTicket(100L, null, null, null, null, null, null, 300L, true, null, null, null, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("is not of type 'problem'")

        when: "target ticket cannot be retrieved"
        tools.updateTicket(100L, null, null, null, null, null, null, 400L, true, null, null, null, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("could not be retrieved")
    }
}


