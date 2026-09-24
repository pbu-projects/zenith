package lol.pbu.tools

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ZendeskIntegrationSpec extends Specification {

    @Shared
    HttpServer server

    @Shared
    ApplicationContext context

    @Shared
    ZendeskTools tools

    def setupSpec() {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v2", new ZendeskHttpHandler())
        server.start()

        context = ApplicationContext.run([
                "micronaut.http.services.zendesk.url": "http://127.0.0.1:${server.address.port}",
                "micronaut.http.services.zendesk.oauth.token": "test-token"
        ])
        tools = context.getBean(ZendeskTools)
    }

    def cleanupSpec() {
        context?.close()
        server?.stop(0)
    }

    def "Help Center articles end-to-end integration via embedded HTTP server"() {
        when: "listing articles"
        def listResp = tools.listArticles("en-us", "title", "asc", 1000L, "guide")

        then:
        listResp != null
        listResp.articles != null
        listResp.articles.size() == 1
        listResp.articles[0].id == 200L

        when: "getting an article"
        def article = tools.getArticle(200L, "en-us")

        then:
        article != null
        article.article.id == 200L
        article.article.title == "Test Article"

        when: "creating an article"
        def created = tools.createArticle(10L, "New Article", "<p>Content</p>", 5L, "en-us", true, ["test"], 1L)

        then:
        created != null
        created.article.id == 201L

        when: "updating an article"
        def updated = tools.updateArticle(200L, "Updated Title", "Updated Body", "en-us", false, 6L, ["updated"], 2L)

        then:
        updated != null
        updated.article.id == 200L
        updated.article.title == "Updated Title"

        when: "deleting an article"
        def deleted = tools.deleteArticle(200L, true, "en-us")

        then:
        deleted.success == true
        deleted.deletedArticleId == 200L
    }

    def "Community tools end-to-end integration via embedded HTTP server"() {
        when: "listing topics"
        def topics = tools.listCommunityTopics()

        then:
        topics != null
        topics.topics.size() == 1
        topics.topics[0].id == 10L
        topics.topics[0].name == "General Discussion"

        when: "getting a topic"
        def topic = tools.getCommunityTopic(10L)

        then:
        topic != null
        topic.topic.id == 10L

        when: "listing posts (all and by topic)"
        def allPosts = tools.listCommunityPosts(null)
        def topicPosts = tools.listCommunityPosts(10L)

        then:
        allPosts.posts.size() == 1
        topicPosts.posts.size() == 1

        when: "getting a single post"
        def post = tools.getCommunityPost(100L)

        then:
        post != null
        post.post.id == 100L
        post.post.title == "Post 100"

        when: "searching community posts"
        def search = tools.searchCommunityPosts("test query")

        then:
        search != null
        search.results.size() == 1
        search.results[0].id == 100L

        when: "listing post comments"
        def comments = tools.listCommunityPostComments(100L)

        then:
        comments != null
        comments.comments.size() == 1
        comments.comments[0].id == 500L
    }

    def "Views, Translations, and Categories end-to-end integration via embedded HTTP server"() {
        when: "views tools"
        def views = tools.listViews()
        def activeViews = tools.listActiveViews()
        def view = tools.getView(50L)
        def viewTickets = tools.getViewTickets(50L)
        def executed = tools.executeView(50L)
        def count = tools.getViewTicketCount(50L)

        then:
        views.views.size() == 1
        activeViews.views.size() == 1
        view.view.id == 50L
        viewTickets.tickets.size() == 1
        executed.rows.size() == 1
        count.viewCount.value == 42

        when: "translations tools"
        def translations = tools.listTranslations("articles", 200L)
        def translation = tools.getTranslation("articles", 200L, "en-us")

        then:
        translations.translations.size() == 1
        translation.translation.id == 300L

        when: "categories tools"
        def categoriesWithLocale = tools.listCategories("en-us")
        def categoriesNoLocale = tools.listCategories(null)
        def categoryWithLocale = tools.getCategory(400L, "en-us")
        def categoryNoLocale = tools.getCategory(400L, null)

        then:
        categoriesWithLocale.categories.size() == 1
        categoriesNoLocale.categories.size() == 1
        categoryWithLocale.category.id == 400L
        categoryNoLocale.category.id == 400L
    }

    def "error handling: upstream HTTP 422 is propagated as HttpClientResponseException and 404 returns null"() {
        when: "404 Not Found"
        def notFound = tools.getCommunityTopic(404L)

        then: "Micronaut declarative HTTP client maps 404 to empty/null"
        notFound == null

        when: "422 Unprocessable Entity"
        tools.getCommunityTopic(422L)

        then: "propagates HttpClientResponseException"
        def e = thrown(HttpClientResponseException)
        e.status.code == 422
        e.response.getBody(String).orElse("").contains("RecordInvalid")
    }

    def "non-numeric ticket ID format in getTickets is handled safely without network call"() {
        when:
        def result = tools.getTickets(["non-numeric-id", "not-a-number"])

        then:
        result != null
        result.tickets.isEmpty()
    }

    static class ZendeskHttpHandler implements HttpHandler {
        @Override
        void handle(HttpExchange exchange) throws IOException {
            String auth = exchange.requestHeaders.getFirst("Authorization")
            if (auth != "Bearer test-token") {
                sendResponse(exchange, 401, '{"error":"Unauthorized"}')
                return
            }

            String method = exchange.requestMethod
            String path = exchange.requestURI.path

            if (path == "/api/v2/community/topics/404") {
                sendResponse(exchange, 404, '{"error":"RecordNotFound","description":"Topic not found"}')
                return
            } else if (path == "/api/v2/community/topics/422") {
                sendResponse(exchange, 422, '{"error":"RecordInvalid","description":"Validation failed"}')
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
            } else {
                sendResponse(exchange, 404, '{"error":"RecordNotFound","description":"Not found: ' + path + '"}')
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(statusCode, bytes.length)
            OutputStream os = exchange.responseBody
            os.write(bytes)
            os.close()
        }
    }
}
