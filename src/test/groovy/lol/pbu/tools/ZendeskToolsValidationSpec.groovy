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
import lol.pbu.client.CustomStatusClient
import lol.pbu.model.CustomStatusesResponse
import lol.pbu.model.CustomStatusResponse
import lol.pbu.model.TicketFormStatus
import lol.pbu.model.TicketFormStatusesResponse
import lol.pbu.model.TicketUpdateInputWithForm
import lol.pbu.z4j.model.Ticket
import lol.pbu.z4j.model.TicketForm
import lol.pbu.z4j.model.TicketFormsResponse
import lol.pbu.z4j.model.TicketCreateRequest
import lol.pbu.z4j.model.TicketFieldCustomStatusObject
import lol.pbu.z4j.model.TicketFieldCustomStatusObjectStatusCategory
import lol.pbu.z4j.model.TicketResponse
import lol.pbu.z4j.model.TicketStatus
import lol.pbu.z4j.model.TicketType
import lol.pbu.z4j.model.TicketUpdateInputPriority
import lol.pbu.z4j.model.TicketUpdateInputStatus
import lol.pbu.z4j.model.TicketUpdateInputType
import lol.pbu.z4j.model.TicketUpdateRequest
import lol.pbu.z4j.model.TicketUpdateResponse
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

class ZendeskToolsValidationSpec extends Specification {

    @TempDir
    Path tempDir

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
    CustomStatusClient customStatusClient = Mock()

    ZendeskTools tools = new ZendeskTools(
            ticketClient, searchClient, ticketFormsClient, customObjectsClient,
            customObjectRecordsClient, attachmentClient, jobStatusClient,
            viewClient, articleClient, categoryClient, translationClient,
            topicClient, postClient, customStatusClient
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
        def r1 = tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, null, null, null, null, "task", null)

        then:
        r1.results().size() == 1
        !r1.results()[0].success()
        r1.results()[0].error().contains("ticket #100 is a parent problem with linked incidents")

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

    def "validateProblemTarget throws when problemId exceeds 32-bit integer range"() {
        when: "problemId exceeds Integer.MAX_VALUE in updateTicket"
        tools.updateTicket(100L, null, null, null, null, null, null, ((Long) Integer.MAX_VALUE) + 1L, true, null, null, null, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("exceeds 32-bit integer range")

        when: "problemId exceeds Integer.MAX_VALUE in batchUpdateTickets"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, ((Long) Integer.MAX_VALUE) + 1L, true, null, null, null, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("exceeds 32-bit integer range")
    }

    def "validateProblemTarget throws when problemId is non-positive"() {
        when: "problemId is zero in updateTicket"
        tools.updateTicket(100L, null, null, null, null, null, null, 0L, true, null, null, null, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message == "problemId must be a positive integer, got: 0"

        when: "problemId is negative in updateTicket"
        tools.updateTicket(100L, null, null, null, null, null, null, -10L, true, null, null, null, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message == "problemId must be a positive integer, got: -10"

        when: "problemId is non-positive in batchUpdateTickets"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, -1L, true, null, null, null, null)

        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message == "problemId must be a positive integer, got: -1"
    }

    def "rejects contradictory parameters when problemId is provided with type other than incident"() {
        when: "updateTicket has problemId and type='task'"
        tools.updateTicket(100L, null, null, null, null, null, null, 200L, true, null, null, "task", null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message == "Cannot specify type 'task' when linking to a problem ticket. Linked tickets must be of type 'incident'."

        when: "updateTicket has problemId and type='problem'"
        tools.updateTicket(100L, null, null, null, null, null, null, 200L, true, null, null, "problem", null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message == "Cannot specify type 'problem' when linking to a problem ticket. Linked tickets must be of type 'incident'."

        when: "batchUpdateTickets has problemId and type='task'"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, false, 200L, true, null, null, "task", null)

        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message == "Cannot specify type 'task' when linking to a problem ticket. Linked tickets must be of type 'incident'."

        when: "batchUpdateTickets with asyncBulk has problemId and type='none'"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, true, 200L, true, null, null, "none", null)

        then:
        def e4 = thrown(IllegalArgumentException)
        e4.message == "Cannot specify type 'none' when linking to a problem ticket. Linked tickets must be of type 'incident'."
    }

    def "batchUpdateTickets with non-numeric IDs returns empty results"() {
        when: "calling batchUpdateTickets with only invalid string IDs"
        def resp = tools.batchUpdateTickets(["abc", "xyz", "   "] as List, null, null, null, null, null, null, false, null, null, null, null, null, null)

        then:
        resp != null
        resp.results().isEmpty()
        resp.jobStatus() == null
        resp.jobStatuses() == null

        when: "calling batchUpdateTickets in asyncBulk mode with only invalid string IDs"
        def respBulk = tools.batchUpdateTickets(["bad-id"] as List, null, null, null, null, null, null, true, null, null, null, null, null, null)

        then:
        respBulk != null
        respBulk.results().isEmpty()
        respBulk.jobStatus() == null
        respBulk.jobStatuses() == null
    }

    def "concurrent batchUpdateTickets isolates ticket validation failure and allows other tickets to succeed"() {
        given:
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(100L).tap {
                id = 100L
                type = TicketType.PROBLEM
                hasIncidents = true
            }
        })
        ticketClient.showTicket(101L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket(101L).tap {
                id = 101L
                type = TicketType.QUESTION
                hasIncidents = false
            }
        })
        ticketClient.updateTicket(101L, _ as TicketUpdateRequest) >> reactor.core.publisher.Mono.just(new TicketUpdateResponse().tap {
            ticket = new Ticket(101L).tap {
                id = 101L
                type = TicketType.TASK
            }
        })

        when: "updating multiple tickets concurrently where 100L fails type validation and 101L succeeds"
        def resp = tools.batchUpdateTickets([100L, 101L], null, null, null, null, null, null, false, null, null, null, null, "task", null)

        then:
        resp != null
        resp.results().size() == 2
        !resp.results()[0].success()
        resp.results()[0].ticketId() == 100L
        resp.results()[0].error().contains("ticket #100 is a parent problem with linked incidents")
        resp.results()[1].success()
        resp.results()[1].ticketId() == 101L
        resp.results()[1].ticket() != null
        resp.results()[1].ticket().id == 101L
    }

    def "validateAndResolveFilePath rejects null, blank, non-existent, directory, and empty files"() {
        when: "filePath is null or blank"
        tools.validateAndResolveFilePath(null)
        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("File path must not be null or empty")

        when: "filePath is blank"
        tools.validateAndResolveFilePath("   ")
        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("File path must not be null or empty")

        when: "filePath does not exist"
        tools.validateAndResolveFilePath("/path/to/definitely/nonexistent/file.txt")
        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message.contains("File not found at path:")

        when: "filePath is a directory"
        File testDir = tempDir.resolve("zenith-test-dir").toFile()
        testDir.mkdirs()
        tools.validateAndResolveFilePath(testDir.absolutePath)
        then:
        def e4 = thrown(IllegalArgumentException)
        e4.message.contains("Path is a directory, not a regular file:")

        when: "filePath is an empty file (0 bytes)"
        File emptyFile = tempDir.resolve("zenith-empty.txt").toFile()
        emptyFile.createNewFile()
        tools.validateAndResolveFilePath(emptyFile.absolutePath)
        then:
        def e5 = thrown(IllegalArgumentException)
        e5.message.contains("Cannot upload empty file (0 bytes):")
    }

    def "validateAndResolveFilePath rejects files exceeding maximum 50MB limit"() {
        given: "a sparse file over 50MB"
        File bigFile = tempDir.resolve("too-large.bin").toFile()
        new RandomAccessFile(bigFile, "rw").withCloseable { raf ->
            raf.setLength(50L * 1024 * 1024 + 1024)
        }

        when:
        tools.validateAndResolveFilePath(bigFile.absolutePath)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("File size exceeds maximum upload limit of 50MB")
    }

    def "validateAndResolveFilePath expands user home tilde correctly"() {
        given: "a temporary directory acting as user home"
        String originalHome = System.getProperty("user.home")
        File fakeHome = tempDir.resolve("fake-home").toFile()
        fakeHome.mkdirs()
        System.setProperty("user.home", fakeHome.absolutePath)
        File tempInHome = new File(fakeHome, "zenith-home-test.txt")
        tempInHome.text = "hello"

        when:
        def resolved = tools.validateAndResolveFilePath("~/zenith-home-test.txt")

        then:
        resolved != null
        resolved.toString() == tempInHome.absolutePath

        cleanup:
        System.setProperty("user.home", originalHome)
    }

    def "resolveTargetFilename resolves base filename and trims custom filenames"() {
        given:
        def p = java.nio.file.Path.of("/tmp/path/to/my-file.txt")

        expect:
        tools.resolveTargetFilename(p, null) == "my-file.txt"
        tools.resolveTargetFilename(p, "   ") == "my-file.txt"
        tools.resolveTargetFilename(p, "custom.png") == "custom.png"
        tools.resolveTargetFilename(p, "/sub/dir/clean.pdf") == "clean.pdf"
    }

    def "validateFilenameExtension validates extensions properly"() {
        when: "valid extensions"
        tools.validateFilenameExtension("test.txt")
        tools.validateFilenameExtension("image.png")
        tools.validateFilenameExtension("my.doc.pdf")
        then:
        noExceptionThrown()

        when: "missing extension"
        tools.validateFilenameExtension("noextension")
        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("Filename must include a valid file extension")

        when: "trailing dot"
        tools.validateFilenameExtension("trailingdot.")
        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("Filename must include a valid file extension")

        when: "dot at start"
        tools.validateFilenameExtension(".hidden")
        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message.contains("Filename must include a valid file extension")
    }

    def "probeContentType detects MIME types by extension with fallback"() {
        given:
        def dummyPath = java.nio.file.Path.of("nonexistent-file.unknown")

        expect:
        tools.probeContentType(dummyPath, "image.png") == "image/png"
        tools.probeContentType(dummyPath, "data.yaml") == "text/yaml"
        tools.probeContentType(dummyPath, "config.yml") == "text/yaml"
        tools.probeContentType(dummyPath, "doc.pdf") == "application/pdf"
        tools.probeContentType(dummyPath, "log.txt") == "text/plain"
        tools.probeContentType(dummyPath, "app.log") == "text/plain"
        tools.probeContentType(dummyPath, "report.csv") == "text/csv"
        tools.probeContentType(dummyPath, "data.json") == "application/json"
        tools.probeContentType(dummyPath, "events.jsonl") == "application/json"
        tools.probeContentType(dummyPath, "notes.md") == "text/markdown"
        tools.probeContentType(dummyPath, "archive.zip") == "application/zip"
        tools.probeContentType(dummyPath, "something.unrecognizedextensionxyz") == "application/octet-stream"
    }

    def "uploadAttachment validates request arguments and calls attachmentClient"() {
        given:
        File tempFile = tempDir.resolve("zenith-upload-valid.txt").toFile()
        tempFile.text = "Hello upload test content"
        def mockResp = new lol.pbu.z4j.model.AttachmentUploadResponse().tap {
            upload = new lol.pbu.z4j.model.AttachmentUploadResponseUpload().tap {
                token = "mock-upload-token-123"
            }
        }
        attachmentClient.uploadAttachment("custom.txt", "text/plain", _ as byte[]) >> reactor.core.publisher.Mono.just(mockResp)

        when: "calling uploadAttachment with valid file and custom filename"
        def resp = tools.uploadAttachment(tempFile.absolutePath, "custom.txt")

        then:
        resp != null
        resp.upload != null
        resp.upload.token == "mock-upload-token-123"

        when: "calling uploadAttachment with unrecognized argument"
        def badReq = new CallToolRequest("uploadAttachment", [filePath: tempFile.absolutePath, bogus: "val"])
        tools.uploadAttachment(tempFile.absolutePath, null, badReq)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unrecognized parameter: 'bogus'")
        e.message.contains("Valid parameters for uploadAttachment are 'filePath' and 'filename'")
    }

    def "uploadAttachment propagates HttpClientResponseException directly without wrapping in generic RuntimeException"() {
        given:
        File tempFile = tempDir.resolve("zenith-upload-fail.png").toFile()
        tempFile.text = "not a real png"
        def httpResponse = io.micronaut.http.HttpResponse.status(io.micronaut.http.HttpStatus.UNPROCESSABLE_ENTITY)
                .body('{"error":"RecordInvalid","description":"The file type and file extension do not match."}')
        def httpEx = new io.micronaut.http.client.exceptions.HttpClientResponseException("Unprocessable Entity", httpResponse)
        attachmentClient.uploadAttachment(*_) >> reactor.core.publisher.Mono.error(httpEx)

        when:
        tools.uploadAttachment(tempFile.absolutePath, null)

        then: "HttpClientResponseException is thrown directly so McpErrorMapper can map it"
        def thrownEx = thrown(io.micronaut.http.client.exceptions.HttpClientResponseException)
        thrownEx.response.code() == 422
    }

    def "resolveUploadTokens successfully uploads files and collects tokens"() {
        given:
        File tempFile = tempDir.resolve("zenith-token-test.txt").toFile()
        tempFile.text = "Upload token test content"
        def mockResp = new lol.pbu.z4j.model.AttachmentUploadResponse().tap {
            upload = new lol.pbu.z4j.model.AttachmentUploadResponseUpload().tap {
                token = "resolved-token-xyz"
            }
        }
        attachmentClient.uploadAttachment(*_) >> reactor.core.publisher.Mono.just(mockResp)

        when:
        def tokens = tools.resolveUploadTokens(["existing-token-abc"], [tempFile.absolutePath])

        then:
        tokens.size() == 2
        tokens[0] == "existing-token-abc"
        tokens[1] == "resolved-token-xyz"
    }

    private List<TicketFieldCustomStatusObject> createSampleCustomStatuses() {
        return [
                new TicketFieldCustomStatusObject().tap {
                    id = 101L
                    agentLabel = "Open - In Progress"
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.OPEN
                    active = true
                    isDefault = true
                    description = "Ticket is being worked on"
                },
                new TicketFieldCustomStatusObject().tap {
                    id = 102L
                    agentLabel = "Waiting on Customer"
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.PENDING
                    active = true
                    isDefault = false
                    description = "Awaiting feedback"
                },
                new TicketFieldCustomStatusObject().tap {
                    id = 103L
                    agentLabel = "Legacy On Hold"
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.HOLD
                    active = false
                    isDefault = false
                    description = "Inactive status"
                },
                new TicketFieldCustomStatusObject().tap {
                    id = 104L
                    agentLabel = "Resolved & Verified"
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.SOLVED
                    active = true
                    isDefault = true
                }
        ]
    }

    def "listCustomStatuses returns compact active-only summary by default to minimize tokens"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when: "calling default listCustomStatuses without args"
        def result = tools.listCustomStatuses()

        then: "only active statuses are returned in compact format"
        result != null
        result.containsKey("custom_statuses")
        def list = (List<Map<String, Object>>) result.get("custom_statuses")
        list.size() == 3
        list.every { it.containsKey("id") && it.containsKey("agent_label") && it.containsKey("status_category") && it.containsKey("active") && it.containsKey("default") }
        // Verify token-efficient projection (no URLs, audit fields, etc.)
        list[0].id == 101L
        list[0].agent_label == "Open - In Progress"
        list[0].status_category == "open"
        list[0].active == true
        list[0].default == true
        list[0].description == "Ticket is being worked on"
        // Inactive status 103 must not be present
        !list.any { it.id == 103L }
    }

    def "listCustomStatuses with includeInactive returns inactive statuses"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when:
        def result = tools.listCustomStatuses(null, true, false)

        then:
        def list = (List<Map<String, Object>>) result.get("custom_statuses")
        list.size() == 4
        list.any { it.id == 103L && it.active == false }
    }

    def "listCustomStatuses with statusCategory filters by category"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when:
        def result = tools.listCustomStatuses("pending", false, false)

        then:
        def list = (List<Map<String, Object>>) result.get("custom_statuses")
        list.size() == 1
        list[0].id == 102L
        list[0].status_category == "pending"
    }

    def "listCustomStatuses with fullPayload returns full model objects"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when:
        def result = tools.listCustomStatuses(null, false, true)

        then:
        def list = (List<TicketFieldCustomStatusObject>) result.get("custom_statuses")
        list.size() == 3
        list[0] instanceof TicketFieldCustomStatusObject
        list[0].id == 101L
    }

    def "listCustomStatuses rejects invalid category and unknown parameters"() {
        when: "invalid category is passed"
        tools.listCustomStatuses("not_a_category", false, false)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid status category: 'not_a_category'")

        when: "unrecognized parameter is passed"
        def badReq = new CallToolRequest("listCustomStatuses", [badParam: "val"])
        tools.listCustomStatuses(null, false, false, badReq)

        then:
        thrown(IllegalArgumentException)
    }

    def "updateTicket sets customStatusId and status when both are provided with matching category"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }

        when:
        tools.updateTicket(100L, "Working on this", "open", "normal", true, null, null, null, null, null, null, null, 101L, null)

        then:
        capturedReq != null
        capturedReq.ticket.status == TicketUpdateInputStatus.OPEN
        capturedReq.ticket.customStatusId == 101
    }

    def "updateTicket accepts custom_status_id in snake_case via CallToolRequest"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }
        def request = new CallToolRequest("updateTicket", [custom_status_id: 101L])

        when:
        tools.updateTicket(100L, null, "open", null, null, null, null, null, null, null, null, null, null, request)

        then:
        capturedReq != null
        capturedReq.ticket.customStatusId == 101
    }

    def "updateTicket validates category match against current ticket when status is omitted"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket().tap {
                id = 100L
                status = TicketStatus.OPEN
            }
        })
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }

        when: "customStatusId has category 'open' matching current ticket status"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, 101L, null)

        then:
        capturedReq != null
        capturedReq.ticket.customStatusId == 101
    }

    def "updateTicket throws actionable error when status category mismatches provided status"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when: "customStatusId 101 has category 'open' but status is 'pending'"
        tools.updateTicket(100L, null, "pending", null, null, null, null, null, null, null, null, null, 101L, null)

        then: "descriptive error is thrown containing valid active custom statuses for 'pending'"
        def e = thrown(IllegalArgumentException)
        e.message.contains("belongs to category 'open', which does not match status 'pending'")
        e.message.contains("Valid active custom statuses for 'pending' are:")
        e.message.contains("102: 'Waiting on Customer'")
    }

    def "updateTicket throws actionable error when category mismatches current ticket and status is omitted"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket().tap {
                id = 100L
                status = TicketStatus.OPEN
            }
        })

        when: "customStatusId 102 has category 'pending' but ticket is 'open'"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, 102L, null)

        then: "error instructs caller to provide the matching 'status' parameter"
        def e = thrown(IllegalArgumentException)
        e.message.contains("belongs to category 'pending', but ticket #100 currently has status 'open'")
        e.message.contains("you must also provide the matching 'status' parameter (status='pending')")
    }

    def "updateTicket throws error on inactive or non-existent customStatusId"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when: "customStatusId 103 is inactive"
        tools.updateTicket(100L, null, "hold", null, null, null, null, null, null, null, null, null, 103L, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("Custom status ID 103 ('Legacy On Hold') is inactive")

        when: "customStatusId 999 does not exist"
        tools.updateTicket(100L, null, "open", null, null, null, null, null, null, null, null, null, 999L, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("Custom status ID 999 does not exist. Active custom statuses are:")
        e2.message.contains("101: 'Open - In Progress'")
    }

    def "updateTicket throws error on invalid customStatusId bounds"() {
        when: "customStatusId is <= 0"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, 0L, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("customStatusId must be a positive integer, got: 0")

        when: "customStatusId exceeds 32-bit Integer range"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, ((Long) Integer.MAX_VALUE) + 1L, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("exceeds 32-bit integer range")
    }

    def "batchUpdateTickets updates tickets with customStatusId in concurrent and bulk modes"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        List<TicketUpdateRequest> immediateReqs = []
        ticketClient.updateTicket(_ as Long, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            immediateReqs.add(req)
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }
        TicketUpdateRequest bulkReq = null
        ticketClient.updateManyTickets(_ as String, _ as TicketUpdateRequest) >> { String ids, TicketUpdateRequest req ->
            bulkReq = req
            return reactor.core.publisher.Mono.just(new JobStatusResponse().tap {
                jobStatus = new JobStatus().tap { id = "bulk-job-1" }
            })
        }

        when: "batch updating in concurrent immediate mode"
        def rImmediate = tools.batchUpdateTickets([100L, 101L], "Batch test", "open", "normal", true, null, null, false, null, null, null, null, null, 101L, null)

        then:
        rImmediate != null
        rImmediate.results().size() == 2
        immediateReqs.size() == 2
        immediateReqs.every { it.ticket.customStatusId == 101 && it.ticket.status == TicketUpdateInputStatus.OPEN }

        when: "batch updating in asyncBulk mode"
        def rBulk = tools.batchUpdateTickets([100L, 101L], "Bulk test", "open", "normal", true, null, null, true, null, null, null, null, null, 101L, null)

        then:
        rBulk != null
        rBulk.jobStatus().id == "bulk-job-1"
        bulkReq != null
        bulkReq.ticket.customStatusId == 101
        bulkReq.ticket.status == TicketUpdateInputStatus.OPEN
    }

    def "createTicket sets customStatusId on TicketCreateInput"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        TicketCreateRequest capturedReq = null
        ticketClient.createTicket(_ as TicketCreateRequest) >> { TicketCreateRequest req ->
            capturedReq = req
            return reactor.core.publisher.Mono.just(new TicketResponse())
        }

        when:
        tools.createTicket("New Ticket", "Initial comment", true, "normal", "open", null, null, null, null, null, 101L, null)

        then:
        capturedReq != null
        capturedReq.ticket.status == TicketUpdateInputStatus.OPEN
        capturedReq.ticket.customStatusId == 101
    }

    def "custom statuses are cached and do not re-fetch from client within TTL"() {
        given:
        1 * customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        ticketClient.updateTicket(_ as Long, _ as TicketUpdateRequest) >> reactor.core.publisher.Mono.just(new TicketUpdateResponse())

        when: "multiple calls are made that require custom status lookup"
        def r1 = tools.listCustomStatuses()
        def r2 = tools.updateTicket(100L, null, "open", null, null, null, null, null, null, null, null, null, 101L, null)
        def r3 = tools.listCustomStatuses("pending", false, false)

        then: "client was only called once, and results were served from cache"
        r1 != null
        r2 != null
        r3 != null
    }

    def "clearCustomStatusCache forces a re-fetch of custom statuses"() {
        given:
        2 * customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when: "calling listCustomStatuses, clearing cache, and calling again"
        def r1 = tools.listCustomStatuses()
        tools.clearCustomStatusCache()
        def r2 = tools.listCustomStatuses()

        then: "both calls succeed with client invoked twice"
        r1 != null
        r2 != null
    }

    def "stale cache is used if subsequent fetch fails"() {
        given: "initial successful fetch populates cache"
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        tools.listCustomStatuses()

        and: "subsequent fetch fails with an exception"
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.error(new RuntimeException("Zendesk 503 Service Unavailable"))

        when: "force refreshing when client errors"
        def cachedResult = tools.getCachedCustomStatuses(true)

        then: "stale cache is returned gracefully instead of throwing"
        cachedResult != null
        cachedResult.size() == 4
    }

    List<TicketFormStatus> createSampleTicketFormStatuses() {
        return [
                new TicketFormStatus("assoc-1", 101L, 1001L),
                new TicketFormStatus("assoc-2", 101L, 1002L),
                new TicketFormStatus("assoc-3", 102L, 1001L),
        ]
    }

    def "listCustomStatuses and listStatusCategories return status_categories grouped response with form context"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        customStatusClient.listTicketFormStatuses(null) >> reactor.core.publisher.Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))

        when: "calling listCustomStatuses"
        def result = tools.listCustomStatuses()

        then: "result contains both custom_statuses and status_categories grouped responses"
        result.containsKey("custom_statuses")
        result.containsKey("status_categories")

        def customStatuses = (List<Map<String, Object>>) result.get("custom_statuses")
        def status101 = customStatuses.find { it.id == 101L }
        status101.ticket_form_ids == [1001L, 1002L]

        def status104 = customStatuses.find { it.id == 104L }
        status104.applies_to_all_forms == true

        def categories = (Map<String, List<Map<String, Object>>>) result.get("status_categories")
        categories.containsKey("open")
        categories.containsKey("pending")
        categories.containsKey("solved")

        def openList = categories.get("open")
        openList.size() == 1
        openList[0].id == 101L
        openList[0].ticket_form_ids == [1001L, 1002L]

        when: "calling listStatusCategories alias"
        def catResult = tools.listStatusCategories()

        then: "produces identical categorized response"
        catResult.status_categories == result.status_categories
    }

    def "listCustomStatuses with ticketFormId filters by form and preserves category defaults"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        customStatusClient.listTicketFormStatuses(null) >> reactor.core.publisher.Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))

        when: "filtering by ticketFormId 1002"
        def result = tools.listCustomStatuses(null, 1002L, false, false)

        then: "only custom statuses valid for form 1002 and category defaults are included"
        result.ticket_form_id == 1002L
        def list = (List<Map<String, Object>>) result.get("custom_statuses")
        // 101 is associated with 1002
        list.any { it.id == 101L }
        // 102 is only associated with 1001, so must be excluded
        !list.any { it.id == 102L }
        // 104 is default for solved category, so must be included
        list.any { it.id == 104L }

        def categories = (Map<String, List<Map<String, Object>>>) result.get("status_categories")
        categories.get("open").any { it.id == 101L }
        categories.get("pending").isEmpty()
        categories.get("solved").any { it.id == 104L }
    }

    def "updateTicket throws actionable error when customStatusId is not allowed on ticket's form"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        customStatusClient.listTicketFormStatuses(null) >> reactor.core.publisher.Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket().tap {
                id = 100L
                status = TicketStatus.PENDING
                ticketFormId = 1002L
            }
        })

        when: "customStatusId 102 is only allowed on form 1001, but ticket has form 1002"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, 102L, null)

        then: "actionable error lists the form mismatch and valid custom statuses for the form"
        def e = thrown(IllegalArgumentException)
        e.message.contains("Custom status ID 102 ('Waiting on Customer') cannot be used with ticket form #1002")
    }

    def "updateTicket succeeds when customStatusId matches ticket form"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        customStatusClient.listTicketFormStatuses(null) >> reactor.core.publisher.Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))
        ticketClient.showTicket(100L) >> reactor.core.publisher.Mono.just(new TicketResponse().tap {
            ticket = new Ticket().tap {
                id = 100L
                status = TicketStatus.OPEN
                ticketFormId = 1002L
            }
        })
        TicketUpdateRequest captured = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            captured = req
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }

        when: "customStatusId 101 is allowed on form 1002"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, 101L, null)

        then:
        captured != null
        captured.ticket.customStatusId == 101
    }

    def "createTicket validates customStatusId against ticket_form_id"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        customStatusClient.listTicketFormStatuses(null) >> reactor.core.publisher.Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))
        TicketCreateRequest captured = null
        ticketClient.createTicket(_ as TicketCreateRequest) >> { TicketCreateRequest req ->
            captured = req
            return reactor.core.publisher.Mono.just(new TicketResponse())
        }

        when: "creating ticket with form 1002 and customStatusId 102 (which is only on 1001)"
        def reqMismatched = new CallToolRequest("createTicket", [
                subject: "Test",
                comment: "Note",
                isPublic: true,
                ticket_form_id: 1002L,
                custom_status_id: 102L,
                status: "pending"
        ])
        tools.createTicket("Test", "Note", true, null, "pending", null, null, null, null, null, 102L, reqMismatched)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Custom status ID 102 ('Waiting on Customer') cannot be used with ticket form #1002")

        when: "creating ticket with form 1001 and customStatusId 102 (which is valid for 1001)"
        def reqValid = new CallToolRequest("createTicket", [
                subject: "Test",
                comment: "Note",
                isPublic: true,
                ticket_form_id: 1001L,
                custom_status_id: 102L,
                status: "pending"
        ])
        tools.createTicket("Test", "Note", true, null, "pending", null, null, null, null, null, 102L, reqValid)

        then:
        captured != null
        captured.ticket.ticketFormId == 1001L
        captured.ticket.customStatusId == 102
    }

    def "ticket form status cache is refreshed after clearCustomStatusCache"() {
        given:
        2 * customStatusClient.listCustomStatuses(null, null) >> reactor.core.publisher.Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        2 * customStatusClient.listTicketFormStatuses(null) >> reactor.core.publisher.Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))

        when: "calling listCustomStatuses twice with clearCustomStatusCache in between"
        tools.listCustomStatuses()
        tools.clearCustomStatusCache()
        tools.listCustomStatuses()

        then: "ticket form statuses were fetched twice"
        notThrown(Exception)
    }

    List<TicketForm> createSampleTicketForms() {
        return [
                new TicketForm().tap {
                    id = 1001L
                    name = "Standard Support Form"
                    displayName = "Standard Support"
                    active = true
                    defaultForm = true
                },
                new TicketForm().tap {
                    id = 1002L
                    name = "Bug Report Form"
                    displayName = "Bug Report"
                    active = true
                    defaultForm = false
                },
                new TicketForm().tap {
                    id = 1003L
                    name = "Legacy Form"
                    displayName = "Legacy"
                    active = false
                    defaultForm = false
                }
        ]
    }

    def "updateTicket sets ticketFormId on TicketUpdateInputWithForm"() {
        given:
        ticketFormsClient.listTicketForms() >> reactor.core.publisher.Mono.just(new TicketFormsResponse(createSampleTicketForms()))
        TicketUpdateRequest capturedReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedReq = req
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }

        when: "calling updateTicket with ticketFormId"
        tools.updateTicket(100L, "Switching to bug form", null, null, true, null, null, null, null, null, null, null, null, 1002L, null)

        then: "captured request contains TicketUpdateInputWithForm with ticketFormId set"
        capturedReq != null
        capturedReq.ticket instanceof TicketUpdateInputWithForm
        ((TicketUpdateInputWithForm) capturedReq.ticket).ticketFormId == 1002L

        when: "calling updateTicket with snake_case ticket_form_id in CallToolRequest"
        capturedReq = null
        def req = new CallToolRequest("updateTicket", [ticket_form_id: 1001L])
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, null, null, req)

        then: "ticket form id is resolved from snake_case request arguments"
        capturedReq != null
        capturedReq.ticket instanceof TicketUpdateInputWithForm
        ((TicketUpdateInputWithForm) capturedReq.ticket).ticketFormId == 1001L
    }

    def "updateTicket validates ticketFormId bounds and active status"() {
        given:
        ticketFormsClient.listTicketForms() >> reactor.core.publisher.Mono.just(new TicketFormsResponse(createSampleTicketForms()))

        when: "ticketFormId is non-positive"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, null, -5L, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("ticketFormId must be a positive integer, got: -5")

        when: "ticketFormId does not exist"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, null, 9999L, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("Ticket form ID 9999 does not exist. Active ticket forms are: [1001: 'Standard Support Form'], [1002: 'Bug Report Form']")

        when: "ticketFormId is inactive"
        tools.updateTicket(100L, null, null, null, null, null, null, null, null, null, null, null, null, 1003L, null)

        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message.contains("Ticket form ID 1003 ('Legacy Form') is inactive.")
    }

    def "batchUpdateTickets sets ticketFormId in concurrent and bulk modes"() {
        given:
        ticketFormsClient.listTicketForms() >> reactor.core.publisher.Mono.just(new TicketFormsResponse(createSampleTicketForms()))
        TicketUpdateRequest capturedConcurrentReq = null
        ticketClient.updateTicket(100L, _ as TicketUpdateRequest) >> { Long id, TicketUpdateRequest req ->
            capturedConcurrentReq = req
            return reactor.core.publisher.Mono.just(new TicketUpdateResponse())
        }
        TicketUpdateRequest capturedBulkReq = null
        ticketClient.updateManyTickets("100", _ as TicketUpdateRequest) >> { String ids, TicketUpdateRequest req ->
            capturedBulkReq = req
            return reactor.core.publisher.Mono.just(new JobStatusResponse(new JobStatus().tap { id = "job-form-1" }))
        }

        when: "running concurrent batch update with ticketFormId"
        tools.batchUpdateTickets([100L], "Updated form", null, null, true, null, null, false, null, null, null, null, null, null, 1001L, null)

        then: "concurrent ticket update input contains ticketFormId"
        capturedConcurrentReq != null
        capturedConcurrentReq.ticket instanceof TicketUpdateInputWithForm
        ((TicketUpdateInputWithForm) capturedConcurrentReq.ticket).ticketFormId == 1001L

        when: "running async bulk batch update with ticketFormId"
        tools.batchUpdateTickets([100L], null, null, null, null, null, null, true, null, null, null, null, null, null, 1002L, null)

        then: "bulk ticket update input contains ticketFormId"
        capturedBulkReq != null
        capturedBulkReq.ticket instanceof TicketUpdateInputWithForm
        ((TicketUpdateInputWithForm) capturedBulkReq.ticket).ticketFormId == 1002L
    }

    def "ticket forms are cached and refreshed via clearTicketFormCache and clearAllCaches"() {
        given:
        2 * ticketFormsClient.listTicketForms() >> reactor.core.publisher.Mono.just(new TicketFormsResponse(createSampleTicketForms()))

        when: "calling listTicketForms multiple times without cache clearing"
        tools.clearTicketFormCache()
        def res1 = tools.listTicketForms()
        def res2 = tools.listTicketForms()

        then: "cached response is reused"
        res1.ticket_forms.size() == 2 // 1001 and 1002 active
        res2.ticket_forms.size() == 2

        when: "clearing ticket form cache"
        tools.clearTicketFormCache()
        def res3 = tools.listTicketForms()

        then: "client was invoked a second time"
        res3.ticket_forms.size() == 2

        when: "testing clearAllCaches clears both custom statuses and ticket forms"
        tools.clearAllCaches()

        then:
        notThrown(Exception)
    }
}




