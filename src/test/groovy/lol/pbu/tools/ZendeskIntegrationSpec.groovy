package lol.pbu.tools

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.exceptions.HttpClientResponseException
import lol.pbu.client.CustomStatusClient
import lol.pbu.model.TicketMutationOptions
import lol.pbu.tools.ZendeskCommunityTools
import lol.pbu.tools.ZendeskCustomObjectTools
import lol.pbu.tools.ZendeskHelpCenterTools
import lol.pbu.tools.ZendeskMetadataTools
import lol.pbu.tools.ZendeskSearchTools
import lol.pbu.tools.ZendeskTicketTools
import lol.pbu.tools.ZendeskViewTools
import spock.lang.Shared
import spock.lang.Specification

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.List
import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList
import reactor.core.publisher.Mono

class CapturedRequest {
    String method
    String path
    String body
    Map<String, List<String>> headers

    CapturedRequest(String method, String path, String body, Map<String, List<String>> headers) {
        this.method = method
        this.path = path
        this.body = body
        this.headers = headers
    }
}

class MonoUnwrappingWrapper implements GroovyInterceptable {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10)
    private final Object delegate

    MonoUnwrappingWrapper(Object delegate) {
        this.delegate = delegate
    }

    @Override
    Object invokeMethod(String name, Object args) {
        def res = delegate.invokeMethod(name, args)
        if (res instanceof Mono) {
            return ((Mono<?>) res).block(DEFAULT_TIMEOUT)
        }
        return res
    }

    def propertyMissing(String name) {
        return delegate."$name"
    }
}

class ZendeskIntegrationSpec extends Specification {

    @Shared
    HttpServer server

    @Shared
    ZendeskHttpHandler handler = new ZendeskHttpHandler()

    @Shared
    ApplicationContext context

    @Shared
    def helpCenterTools

    @Shared
    def communityTools

    @Shared
    def viewTools

    @Shared
    def ticketTools

    @Shared
    def metadataTools

    @Shared
    def customObjectTools

    @Shared
    def searchTools

    @Shared
    CustomStatusClient customStatusClient

    def setupSpec() {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v2", handler)
        server.start()

        context = ApplicationContext.run([
                "micronaut.http.services.zendesk.url": "http://127.0.0.1:${server.address.port}",
                "micronaut.http.services.zendesk.oauth.token": "test-token"
        ])
        helpCenterTools = new MonoUnwrappingWrapper(context.getBean(ZendeskHelpCenterTools))
        communityTools = new MonoUnwrappingWrapper(context.getBean(ZendeskCommunityTools))
        viewTools = new MonoUnwrappingWrapper(context.getBean(ZendeskViewTools))
        ticketTools = new MonoUnwrappingWrapper(context.getBean(ZendeskTicketTools))
        metadataTools = new MonoUnwrappingWrapper(context.getBean(ZendeskMetadataTools))
        customObjectTools = new MonoUnwrappingWrapper(context.getBean(ZendeskCustomObjectTools))
        searchTools = new MonoUnwrappingWrapper(context.getBean(ZendeskSearchTools))
        customStatusClient = context.getBean(CustomStatusClient)
    }

    def cleanupSpec() {
        context?.close()
        server?.stop(0)
    }

    def "Help Center articles end-to-end integration via embedded HTTP server"() {
        when: "listing articles"
        def listResp = helpCenterTools.listArticles("en-us", "title", "asc", 1000L, "guide")

        then:
        listResp != null
        listResp.articles != null
        listResp.articles.size() == 1
        listResp.articles[0].id == 200L

        when: "getting an article"
        def article = helpCenterTools.getArticle(200L, "en-us")

        then:
        article != null
        article.article.id == 200L
        article.article.title == "Test Article"

        when: "creating an article"
        def created = helpCenterTools.createArticle(10L, "New Article", "<p>Content</p>", 5L, "en-us", true, ["test"], 1L)

        then:
        created != null
        created.article.id == 201L

        when: "updating an article"
        def updated = helpCenterTools.updateArticle(200L, "Updated Title", "Updated Body", "en-us", false, 6L, ["updated"], 2L)

        then:
        updated != null
        updated.article.id == 200L
        updated.article.title == "Updated Title"

        when: "deleting an article"
        def deleted = helpCenterTools.deleteArticle(200L, true, "en-us")

        then:
        deleted.success == true
        deleted.deletedArticleId == 200L
    }

    def "Community tools end-to-end integration via embedded HTTP server"() {
        when: "listing topics"
        def topics = communityTools.listCommunityTopics()

        then:
        topics != null
        topics.topics.size() == 1
        topics.topics[0].id == 10L
        topics.topics[0].name == "General Discussion"

        when: "getting a topic"
        def topic = communityTools.getCommunityTopic(10L)

        then:
        topic != null
        topic.topic.id == 10L

        when: "listing posts (all and by topic)"
        def allPosts = communityTools.listCommunityPosts(null)
        def topicPosts = communityTools.listCommunityPosts(10L)

        then:
        allPosts.posts.size() == 1
        topicPosts.posts.size() == 1

        when: "getting a single post"
        def post = communityTools.getCommunityPost(100L)

        then:
        post != null
        post.post.id == 100L
        post.post.title == "Post 100"

        when: "searching community posts"
        def search = communityTools.searchCommunityPosts("test query")

        then:
        search != null
        search.results.size() == 1
        search.results[0].id == 100L

        when: "listing post comments"
        def comments = communityTools.listCommunityPostComments(100L)

        then:
        comments != null
        comments.comments.size() == 1
        comments.comments[0].id == 500L
    }

    def "Views, Translations, and Categories end-to-end integration via embedded HTTP server"() {
        when: "views tools"
        def views = viewTools.listViews()
        def activeViews = viewTools.listActiveViews()
        def view = viewTools.getView(50L)
        def viewTickets = viewTools.getViewTickets(50L)
        def executed = viewTools.executeView(50L)
        def count = viewTools.getViewTicketCount(50L)

        then:
        views.views.size() == 1
        activeViews.views.size() == 1
        view.view.id == 50L
        viewTickets.tickets.size() == 1
        executed.rows.size() == 1
        count.viewCount.value == 42

        when: "translations tools"
        def translations = helpCenterTools.listTranslations("articles", 200L)
        def translation = helpCenterTools.getTranslation("articles", 200L, "en-us")

        then:
        translations.translations.size() == 1
        translation.translation.id == 300L

        when: "categories tools"
        def categoriesWithLocale = helpCenterTools.listCategories("en-us")
        def categoriesNoLocale = helpCenterTools.listCategories(null)
        def categoryWithLocale = helpCenterTools.getCategory(400L, "en-us")
        def categoryNoLocale = helpCenterTools.getCategory(400L, null)

        then:
        categoriesWithLocale.categories.size() == 1
        categoriesNoLocale.categories.size() == 1
        categoryWithLocale.category.id == 400L
        categoryNoLocale.category.id == 400L
    }

    def "error handling: upstream HTTP 422 is propagated as HttpClientResponseException and 404 returns null"() {
        when: "404 Not Found"
        def notFound = communityTools.getCommunityTopic(404L)

        then: "Micronaut declarative HTTP client maps 404 to empty/null"
        notFound == null

        when: "422 Unprocessable Entity"
        communityTools.getCommunityTopic(422L)

        then: "propagates HttpClientResponseException"
        def e = thrown(HttpClientResponseException)
        e.status.code == 422
        e.response.getBody(String).orElse("").contains("RecordInvalid")
    }

    def "non-numeric ticket ID format in getTickets is handled safely without network call"() {
        when:
        def result = ticketTools.getTickets(["non-numeric-id", "not-a-number"])

        then:
        result != null
        result.tickets.isEmpty()
    }

    def "Ticket audits end-to-end integration with AutomaticAnswerSend and complex events via embedded HTTP server"() {
        when: "calling getTicketAudits on ticket 123"
        def auditsResp = ticketTools.getTicketAudits(123L)

        then: "audits containing AutomaticAnswerSend and custom events are returned"
        auditsResp != null
        auditsResp.audits.size() == 1
        def events = auditsResp.audits[0].events
        events.size() == 3
        events.find { it.type == "AutomaticAnswerSend" } != null
        events.find { it.type == "AutomaticAnswerSend" }.body.contains("Suggested Article")
        events.find { it.type == "KnowledgeRequested" } != null
        events.find { it.type == "KnowledgeRequested" }.body.contains("Help Guide")
        events.find { it.type == "UnknownCustomEvent" } != null
    }


    def "Custom statuses and categories end-to-end integration via embedded HTTP server"() {
        when: "fetching custom statuses directly from declarative client"
        def clientResp = customStatusClient.listCustomStatuses(null, null).block(Duration.ofSeconds(10))

        then:
        clientResp != null
        clientResp.customStatuses().size() == 2
        clientResp.customStatuses()[0].id == 101L
        clientResp.customStatuses()[0].agentLabel == "Investigating"
        handler.findLastRequest("GET", "/api/v2/custom_statuses") != null

        when: "fetching single custom status"
        def singleResp = customStatusClient.showCustomStatus(101L).block(Duration.ofSeconds(10))

        then:
        singleResp != null
        singleResp.customStatus().id == 101L
        handler.findLastRequest("GET", "/api/v2/custom_statuses/101") != null

        when: "listing custom statuses via metadataTools"
        def toolResp = metadataTools.listCustomStatuses()

        then:
        toolResp != null
        toolResp.containsKey("custom_statuses")

        when: "listing status categories via metadataTools"
        def categoriesResp = metadataTools.listStatusCategories()

        then:
        categoriesResp != null
        categoriesResp.containsKey("status_categories")
    }

    def "Ticket lifecycle mutations and reads end-to-end integration via embedded HTTP server"() {
        when: "getting ticket count"
        def countResp = ticketTools.getTicketCount()

        then:
        countResp != null
        countResp.count.value == 42

        when: "getting a single ticket"
        def ticketResp = ticketTools.getTicket(1L)

        then:
        ticketResp != null
        ticketResp.ticket.id == 1L
        ticketResp.ticket.customStatusId == 101L

        when: "getting multiple tickets"
        def ticketsResp = ticketTools.getTickets([1L])

        then:
        ticketsResp != null
        ticketsResp.tickets.size() == 1
        ticketsResp.tickets[0].id == 1L

        when: "listing tickets"
        def listTicketsResp = ticketTools.listTickets()

        then:
        listTicketsResp != null
        listTicketsResp.tickets.size() == 1

        when: "creating a ticket with customStatusId, ticketFormId, and tags"
        def createdTicket = ticketTools.createTicket(
                "Created Subject",
                "Created comment body",
                true,
                "normal",
                "open",
                null,
                null,
                null,
                null,
                null,
                101L,
                1001L,
                null,
                null,
                ["tag1"],
                null
        )

        then:
        createdTicket != null
        createdTicket.ticket.id == 1L
        createdTicket.ticket.customStatusId == 101L
        createdTicket.ticket.ticketFormId == 1001L

        and: "inbound wire POST payload contains custom_status_id, ticket_form_id, and tags"
        def createReq = handler.findLastRequest("POST", "/api/v2/tickets")
        createReq != null
        def createPayload = new JsonSlurper().parseText(createReq.body)
        createPayload.ticket.subject == "Created Subject"
        createPayload.ticket.custom_status_id == 101
        createPayload.ticket.ticket_form_id == 1001
        createPayload.ticket.tags == ["tag1"]

        when: "updating a ticket with customStatusId and form"
        def updatedTicket = ticketTools.updateTicket(
                1L,
                "Updated comment body",
                "open",
                "high",
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                101L,
                1001L,
                null,
                null,
                ["new_tag"],
                null
        )

        then:
        updatedTicket != null
        updatedTicket.ticket.id == 1L
        updatedTicket.ticket.customStatusId == 101L

        and: "inbound wire PUT payload for update contains custom_status_id, ticket_form_id, and tags"
        def updateReq = handler.findLastRequest("PUT", "/api/v2/tickets/1")
        updateReq != null
        def updatePayload = new JsonSlurper().parseText(updateReq.body)
        updatePayload.ticket.custom_status_id == 101
        updatePayload.ticket.ticket_form_id == 1001
        updatePayload.ticket.tags == ["new_tag"]

        when: "batch updating tickets concurrently"
        def batchImmediate = ticketTools.batchUpdateTickets(
                [1L],
                "Batch update comment",
                "open",
                "normal",
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                101L,
                1001L,
                null,
                null,
                null,
                null,
                null
        )

        then:
        batchImmediate != null
        batchImmediate.results().size() == 1
        batchImmediate.results()[0].success()

        when: "batch updating tickets asynchronously via bulk job"
        def batchBulk = ticketTools.batchUpdateTickets(
                [1L],
                "Bulk update comment",
                "open",
                "normal",
                false,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                101L,
                1001L,
                null,
                null,
                null,
                null,
                null
        )

        then:
        batchBulk != null
        batchBulk.jobStatus().id == "bulk-job-123"

        and: "inbound wire PUT payload for bulk update contains ticket with custom_status_id and ticket_form_id"
        def bulkReq = handler.findLastRequest("PUT", "/api/v2/tickets/update_many")
        bulkReq != null
        def bulkPayload = new JsonSlurper().parseText(bulkReq.body)
        bulkPayload.ticket.custom_status_id == 101
        bulkPayload.ticket.ticket_form_id == 1001

        when: "getting job status"
        def jobStatus = ticketTools.getJobStatus("bulk-job-123")

        then:
        jobStatus != null
        jobStatus.jobStatus.status == "completed"

        when: "uploading attachment"
        File tempFile = File.createTempFile("zenith-int-up-", ".txt")
        tempFile.text = "Integration test attachment content"
        def uploadResp = ticketTools.uploadAttachment(tempFile.absolutePath, "test.txt")

        then:
        uploadResp != null
        uploadResp.upload.token == "upload-tok-123"

        and: "inbound wire request for upload sent the file content"
        def uploadReq = handler.findLastRequest("POST", "/api/v2/uploads")
        uploadReq != null
        uploadReq.body.contains("Integration test attachment content")

        cleanup:
        tempFile?.delete()
    }

    def "Ticket forms and ticket fields discovery via embedded HTTP server"() {
        when: "listing ticket forms"
        def formsResp = metadataTools.listTicketForms()

        then:
        formsResp != null
        formsResp.containsKey("ticket_forms")

        when: "getting ticket form by ID"
        def formResp = metadataTools.getTicketForm(1001L)

        then:
        formResp != null
        formResp.ticketForm.id == 1001L
        formResp.ticketForm.name == "Standard Support Form"

        when: "listing ticket fields"
        def fieldsResp = ticketTools.listTicketFields()

        then:
        fieldsResp != null
        fieldsResp.ticketFields.size() == 1
        fieldsResp.ticketFields[0].id == 10L

        when: "getting ticket field by ID"
        def fieldResp = ticketTools.getTicketField(10L)

        then:
        fieldResp != null
        fieldResp.ticketField.id == 10L
        fieldResp.ticketField.title == "Sample Field"
    }

    def "Search and searchCount end-to-end integration via embedded HTTP server"() {
        when: "searching tickets"
        def searchResp = searchTools.search("type:ticket", "users", 5)

        then:
        searchResp != null
        searchResp.results.size() == 1
        searchResp.results[0].id == 1L

        when: "counting search results"
        def countResp = searchTools.searchCount("type:ticket")

        then:
        countResp != null
        countResp.count == 5
    }

    def "Custom objects and records CRUD via embedded HTTP server"() {
        when: "listing custom objects"
        def objectsResp = customObjectTools.listCustomObjects()

        then:
        objectsResp != null
        objectsResp.customObjects.size() == 1
        objectsResp.customObjects[0].key == "car"

        when: "getting custom object limits"
        def limitsResp = customObjectTools.getCustomObjectLimits()

        then:
        limitsResp != null
        limitsResp.limit == 100L
        limitsResp.count == 10L

        when: "getting custom object details"
        def objResp = customObjectTools.getCustomObject("car")

        then:
        objResp != null
        objResp.customObject.key == "car"

        when: "creating custom object"
        def createObj = customObjectTools.createCustomObject("car", "Car", "Cars", "Description", null, null)

        then:
        createObj != null
        createObj.customObject.key == "car"

        and: "inbound wire payload for createCustomObject has key and titles"
        def createObjReq = handler.findLastRequest("POST", "/api/v2/custom_objects")
        createObjReq != null
        def createObjPayload = new JsonSlurper().parseText(createObjReq.body)
        createObjPayload.custom_object.key == "car"
        createObjPayload.custom_object.title == "Car"
        createObjPayload.custom_object.title_pluralized == "Cars"

        when: "updating custom object"
        def updateObj = customObjectTools.updateCustomObject("car", "Updated Car", "Cars", null, null, null)

        then:
        updateObj != null
        updateObj.customObject.title == "Updated Car"

        and: "inbound wire payload for updateCustomObject was sent via PATCH with updated title"
        def updateObjReq = handler.findLastRequest("PATCH", "/api/v2/custom_objects/car")
        updateObjReq != null
        def updateObjPayload = new JsonSlurper().parseText(updateObjReq.body)
        updateObjPayload.custom_object.title == "Updated Car"

        when: "deleting custom object"
        def deleteObj = customObjectTools.deleteCustomObject("car")

        then:
        deleteObj != null
        deleteObj.success == true

        when: "listing custom object records"
        def recordsResp = customObjectTools.listCustomObjectRecords("car")

        then:
        recordsResp != null
        recordsResp.customObjectRecords.size() == 1
        recordsResp.customObjectRecords[0].id == "rec-1"

        when: "getting single custom object record"
        def recResp = customObjectTools.getCustomObjectRecord("car", "rec-1")

        then:
        recResp != null
        recResp.customObjectRecord.id == "rec-1"

        when: "creating custom object record"
        def createRec = customObjectTools.createCustomObjectRecord("car", "Tesla Model 3", [color: "blue"], "ext-1", null, null)

        then:
        createRec != null
        createRec.customObjectRecord.id == "rec-1"

        and: "inbound wire payload for createCustomObjectRecord contains name, external_id, and fields"
        def createRecReq = handler.findLastRequest("POST", "/api/v2/custom_objects/car/records")
        createRecReq != null
        def createRecPayload = new JsonSlurper().parseText(createRecReq.body)
        createRecPayload.custom_object_record.name == "Tesla Model 3"
        createRecPayload.custom_object_record.external_id == "ext-1"
        createRecPayload.custom_object_record.custom_object_fields.color == "blue"

        when: "updating custom object record"
        def updateRec = customObjectTools.updateCustomObjectRecord("car", "rec-1", "Tesla Model 3 Updated", [color: "red"], "ext-1", null, null)

        then:
        updateRec != null
        updateRec.customObjectRecord.name == "Tesla Model 3 Updated"

        and: "inbound wire payload for updateCustomObjectRecord was sent via PATCH with updated fields"
        def updateRecReq = handler.findLastRequest("PATCH", "/api/v2/custom_objects/car/records/rec-1")
        updateRecReq != null
        def updateRecPayload = new JsonSlurper().parseText(updateRecReq.body)
        updateRecPayload.custom_object_record.name == "Tesla Model 3 Updated"
        updateRecPayload.custom_object_record.custom_object_fields.color == "red"

        when: "deleting custom object record"
        def deleteRec = customObjectTools.deleteCustomObjectRecord("car", "rec-1")

        then:
        deleteRec != null
        deleteRec.success == true

        when: "searching custom object records"
        def searchRec = customObjectTools.searchCustomObjectRecords("car", "Tesla")

        then:
        searchRec != null
        searchRec.customObjectRecords.size() == 1
        searchRec.customObjectRecords[0].id == "rec-1"
    }

    static class ZendeskHttpHandler implements HttpHandler {
        final List<CapturedRequest> requests = new CopyOnWriteArrayList<>()

        CapturedRequest findLastRequest(String method, String pathPrefix) {
            return requests.reverse().find { it.method == method && it.path.startsWith(pathPrefix) }
        }

        void clearRequests() {
            requests.clear()
        }

        @Override
        void handle(HttpExchange exchange) throws IOException {
            String method = exchange.requestMethod
            String path = exchange.requestURI.path
            byte[] bodyBytes = exchange.requestBody.readAllBytes()
            String body = new String(bodyBytes, StandardCharsets.UTF_8)
            requests.add(new CapturedRequest(method, path, body, exchange.requestHeaders))

            String auth = exchange.requestHeaders.getFirst("Authorization")
            if (auth != "Bearer test-token") {
                sendResponse(exchange, 401, '{"error":"Unauthorized"}')
                return
            }

            String normPath = path.endsWith(".json") ? path.substring(0, path.length() - 5) : path

            if (path == "/api/v2/community/topics/404") {
                sendResponse(exchange, 404, '{"error":"RecordNotFound","description":"Topic not found"}')
                return
            } else if (path == "/api/v2/community/topics/422") {
                sendResponse(exchange, 422, '{"error":"RecordInvalid","description":"Validation failed"}')
                return
            }

            // Custom statuses & form associations
            if (normPath == "/api/v2/custom_statuses") {
                sendResponse(exchange, 200, '{"custom_statuses":[{"id":101,"status_category":"open","agent_label":"Investigating","active":true,"default":true},{"id":102,"status_category":"pending","agent_label":"Waiting on Customer","active":true,"default":false}]}')
                return
            } else if (normPath == "/api/v2/custom_statuses/101") {
                sendResponse(exchange, 200, '{"custom_status":{"id":101,"status_category":"open","agent_label":"Investigating","active":true,"default":true}}')
                return
            } else if (normPath == "/api/v2/ticket_form_statuses") {
                sendResponse(exchange, 200, '{"ticket_form_statuses":[{"id":"assoc-1","custom_status_id":101,"ticket_form_id":1001}]}')
                return
            }

            // Ticket forms
            if (normPath == "/api/v2/ticket_forms") {
                sendResponse(exchange, 200, '{"ticket_forms":[{"id":1001,"name":"Standard Support Form","active":true,"default":true}]}')
                return
            } else if (normPath == "/api/v2/ticket_forms/1001") {
                sendResponse(exchange, 200, '{"ticket_form":{"id":1001,"name":"Standard Support Form","active":true,"default":true}}')
                return
            }

            // Ticket fields
            if (normPath == "/api/v2/ticket_fields") {
                sendResponse(exchange, 200, '{"ticket_fields":[{"id":10,"title":"Sample Field","type":"text","active":true}]}')
                return
            } else if (normPath == "/api/v2/ticket_fields/10") {
                sendResponse(exchange, 200, '{"ticket_field":{"id":10,"title":"Sample Field","type":"text","active":true}}')
                return
            }

            // Search
            if (normPath == "/api/v2/search/count") {
                sendResponse(exchange, 200, '{"count":5}')
                return
            } else if (normPath == "/api/v2/search") {
                sendResponse(exchange, 200, '{"results":[{"id":1,"result_type":"ticket","subject":"Search Result Ticket","status":"open"}],"count":1,"next_page":null}')
                return
            }

            // Uploads
            if (normPath == "/api/v2/uploads") {
                sendResponse(exchange, 201, '{"upload":{"token":"upload-tok-123","attachment":{"id":888,"file_name":"sample.txt"}}}')
                return
            }

            // Job status
            if (normPath == "/api/v2/job_statuses/bulk-job-123") {
                sendResponse(exchange, 200, '{"job_status":{"id":"bulk-job-123","status":"completed"}}')
                return
            }

            // Tickets
            if (normPath == "/api/v2/tickets/count") {
                sendResponse(exchange, 200, '{"count":{"value":42,"refreshed_at":"2026-01-01T00:00:00Z"}}')
                return
            } else if (normPath == "/api/v2/tickets/show_many") {
                sendResponse(exchange, 200, '{"tickets":[{"id":1,"subject":"Ticket 1","status":"open","requester_id":100}]}')
                return
            } else if (normPath == "/api/v2/tickets/update_many") {
                sendResponse(exchange, 200, '{"job_status":{"id":"bulk-job-123","status":"queued"}}')
                return
            } else if (normPath == "/api/v2/tickets") {
                if (method == "POST") {
                    sendResponse(exchange, 201, '{"ticket":{"id":1,"subject":"Created Ticket","status":"open","custom_status_id":101,"ticket_form_id":1001,"requester_id":100}}')
                } else {
                    sendResponse(exchange, 200, '{"tickets":[{"id":1,"subject":"Ticket 1","status":"open","requester_id":100}]}')
                }
                return
            } else if (normPath == "/api/v2/tickets/1") {
                if (method == "PUT") {
                    sendResponse(exchange, 200, '{"ticket":{"id":1,"subject":"Updated Ticket","status":"open","custom_status_id":101,"ticket_form_id":1001,"requester_id":100}}')
                } else {
                    sendResponse(exchange, 200, '{"ticket":{"id":1,"subject":"Ticket 1","status":"open","custom_status_id":101,"ticket_form_id":1001,"tags":["sample_tag"],"requester_id":100}}')
                }
                return
            }

            // Custom Objects
            if (normPath == "/api/v2/custom_objects/limits/object_limit") {
                sendResponse(exchange, 200, '{"count":10,"limit":100}')
                return
            } else if (normPath == "/api/v2/custom_objects") {
                if (method == "POST") {
                    sendResponse(exchange, 201, '{"custom_object":{"key":"car","title":"Car","title_pluralized":"Cars"}}')
                } else {
                    sendResponse(exchange, 200, '{"custom_objects":[{"key":"car","title":"Car","title_pluralized":"Cars"}]}')
                }
                return
            } else if (normPath == "/api/v2/custom_objects/car") {
                if (method == "DELETE") {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                } else if (method == "PUT" || method == "PATCH") {
                    sendResponse(exchange, 200, '{"custom_object":{"key":"car","title":"Updated Car","title_pluralized":"Cars"}}')
                } else {
                    sendResponse(exchange, 200, '{"custom_object":{"key":"car","title":"Car","title_pluralized":"Cars"}}')
                }
                return
            } else if (normPath == "/api/v2/custom_objects/car/records") {
                if (method == "POST") {
                    sendResponse(exchange, 201, '{"custom_object_record":{"id":"rec-1","name":"Tesla Model 3","external_id":"ext-1","custom_object_fields":{"color":"blue"}}}')
                } else {
                    sendResponse(exchange, 200, '{"custom_object_records":[{"id":"rec-1","name":"Tesla Model 3","external_id":"ext-1","custom_object_fields":{"color":"blue"}}],"meta":{"has_more":false}}')
                }
                return
            } else if (normPath == "/api/v2/custom_objects/car/records/rec-1") {
                if (method == "DELETE") {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                } else if (method == "PUT" || method == "PATCH") {
                    sendResponse(exchange, 200, '{"custom_object_record":{"id":"rec-1","name":"Tesla Model 3 Updated","external_id":"ext-1","custom_object_fields":{"color":"red"}}}')
                } else {
                    sendResponse(exchange, 200, '{"custom_object_record":{"id":"rec-1","name":"Tesla Model 3","external_id":"ext-1","custom_object_fields":{"color":"blue"}}}')
                }
                return
            } else if (normPath == "/api/v2/custom_objects/car/records/search") {
                sendResponse(exchange, 200, '{"custom_object_records":[{"id":"rec-1","name":"Tesla Model 3","external_id":"ext-1","custom_object_fields":{"color":"blue"}}],"meta":{"has_more":false}}')
                return
            }

            if (path == "/api/v2/community/topics") {
                sendResponse(exchange, 200, '{"topics":[{"id":10,"name":"General Discussion"}]}')
            } else if (path == "/api/v2/community/topics/10") {
                sendResponse(exchange, 200, '{"topic":{"id":10,"name":"General Discussion"}}')
            } else if (path == "/api/v2/community/topics/10/posts") {
                sendResponse(exchange, 200, '{"posts":[{"id":100,"title":"Post 100","topic_id":10}]}')
            } else if (path == "/api/v2/community/posts") {
                sendResponse(exchange, 200, '{"posts":[{"id":100,"title":"Post 100"}]}')
            } else if (path == "/api/v2/community/posts/100") {
                sendResponse(exchange, 200, '{"post":{"id":100,"title":"Post 100"}}')
            } else if (path == "/api/v2/community/posts/search") {
                sendResponse(exchange, 200, '{"results":[{"id":100,"title":"Post 100"}]}')
            } else if (path == "/api/v2/community/posts/100/comments") {
                sendResponse(exchange, 200, '{"comments":[{"id":500,"body":"Great post","post_id":100}]}')
            } else if (path == "/api/v2/help_center/en-us/articles") {
                sendResponse(exchange, 200, '{"articles":[{"id":200,"title":"Test Article","locale":"en-us","permission_group_id":1}]}')
            } else if (path == "/api/v2/help_center/en-us/articles/200") {
                if (method == "DELETE") {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                } else if (method == "PUT") {
                    sendResponse(exchange, 200, '{"article":{"id":200,"title":"Updated Title","locale":"en-us","permission_group_id":1}}')
                } else {
                    sendResponse(exchange, 200, '{"article":{"id":200,"title":"Test Article","locale":"en-us","permission_group_id":1}}')
                }
            } else if (path == "/api/v2/help_center/en-us/sections/10/articles") {
                sendResponse(exchange, 201, '{"article":{"id":201,"title":"New Article","section_id":10,"locale":"en-us","permission_group_id":1}}')
            } else if (path == "/api/v2/help_center/articles/200/translations") {
                sendResponse(exchange, 200, '{"translations":[{"id":300,"locale":"en-us","title":"Translation"}]}')
            } else if (path == "/api/v2/help_center/articles/200/translations/en-us") {
                sendResponse(exchange, 200, '{"translation":{"id":300,"locale":"en-us","title":"Translation"}}')
            } else if (path == "/api/v2/help_center/en-us/categories") {
                sendResponse(exchange, 200, '{"categories":[{"id":400,"name":"Category 1","locale":"en-us"}]}')
            } else if (path == "/api/v2/help_center/categories") {
                sendResponse(exchange, 200, '{"categories":[{"id":400,"name":"Category 1"}]}')
            } else if (path == "/api/v2/help_center/en-us/categories/400") {
                sendResponse(exchange, 200, '{"category":{"id":400,"name":"Category 1","locale":"en-us"}}')
            } else if (path == "/api/v2/help_center/categories/400") {
                sendResponse(exchange, 200, '{"category":{"id":400,"name":"Category 1"}}')
            } else if (path == "/api/v2/views") {
                sendResponse(exchange, 200, '{"views":[{"id":50,"title":"All Unsolved"}]}')
            } else if (path == "/api/v2/views/active") {
                sendResponse(exchange, 200, '{"views":[{"id":50,"title":"Active Views"}]}')
            } else if (path == "/api/v2/views/50") {
                sendResponse(exchange, 200, '{"view":{"id":50,"title":"View 50"}}')
            } else if (path == "/api/v2/views/50/execute") {
                sendResponse(exchange, 200, '{"rows":[{"ticket":{"id":1,"subject":"Ticket 1","requester_id":100}}],"columns":[]}')
            } else if (path == "/api/v2/views/50/count") {
                sendResponse(exchange, 200, '{"view_count":{"view_id":50,"value":42}}')
            } else if (path == "/api/v2/views/50/tickets.json") {
                sendResponse(exchange, 200, '{"tickets":[{"id":1,"subject":"Ticket 1","requester_id":100}]}')
            } else if (path == "/api/v2/tickets/123/audits") {
                sendResponse(exchange, 200, '{"audits":[{"id":1,"ticket_id":123,"events":[{"id":10,"type":"AutomaticAnswerSend","body":[{"title":"Suggested Article"}]},{"id":11,"type":"KnowledgeRequested","body":{"topic":"Help Guide"}},{"id":12,"type":"UnknownCustomEvent","foo":"bar","body":"regular string"}]}],"count":1}')
            } else {
                sendResponse(exchange, 404, '{"error":"RecordNotFound","description":"Not found: ' + path + '"}')
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(statusCode, bytes.length)
            try (OutputStream os = exchange.responseBody) {
                os.write(bytes)
            }
        }
    }
}
