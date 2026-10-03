package lol.pbu.auth

import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class ZendeskTokenProviderSpec extends Specification {

    static class FakeHttpClient extends HttpClient {
        Closure sendAsyncClosure

        @Override
        Optional<CookieHandler> cookieHandler() { Optional.empty() }
        @Override
        Optional<Duration> connectTimeout() { Optional.empty() }
        @Override
        HttpClient.Redirect followRedirects() { HttpClient.Redirect.NEVER }
        @Override
        Optional<ProxySelector> proxy() { Optional.empty() }
        @Override
        SSLContext sslContext() { SSLContext.default }
        @Override
        SSLParameters sslParameters() { new SSLParameters() }
        @Override
        Optional<Authenticator> authenticator() { Optional.empty() }
        @Override
        HttpClient.Version version() { HttpClient.Version.HTTP_2 }
        @Override
        Optional<Executor> executor() { Optional.empty() }
        @Override
        <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { null }
        @Override
        <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            sendAsyncClosure ? (CompletableFuture<HttpResponse<T>>) sendAsyncClosure(request, responseBodyHandler) : null
        }
        @Override
        <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { null }
    }

    HttpResponse<String> fakeResponse(int status, String bodyText) {
        return [
            statusCode: { -> status },
            body: { -> bodyText }
        ] as HttpResponse<String>
    }

    ObjectMapper objectMapper = ObjectMapper.getDefault()

    def "returns static token when configured"() {
        given:
        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "", "", "my-static-token", "read", objectMapper)

        expect:
        provider.getValidToken() == Optional.of("my-static-token")
    }

    def "returns empty when credentials are missing"() {
        given:
        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "", "", null, "read", objectMapper)

        expect:
        provider.getValidToken() == Optional.empty()
    }

    def "clears expired token and returns empty when refresh fails"() {
        given:
        def provider = new ZendeskTokenProvider("http://localhost:1/", "client-id", "client-secret", null, "read", objectMapper)
        provider.cachedToken = "old-expired-token"
        provider.tokenExpiresAtMs = System.currentTimeMillis() - 100_000L

        when:
        def result = provider.getValidToken()

        then:
        result == Optional.empty()
        provider.cachedToken == null
    }

    def "getValidTokenMono returns static token when configured"() {
        given:
        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "", "", "my-static-token", "read", objectMapper)

        expect:
        provider.getValidTokenMono().block() == "my-static-token"
    }

    def "getValidTokenMono returns empty when credentials are missing"() {
        given:
        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "", "", null, "read", objectMapper)

        expect:
        provider.getValidTokenMono().blockOptional().isEmpty()
    }

    def "getValidTokenMono returns cached token when not expired"() {
        given:
        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "cid", "csecret", null, "read", objectMapper)
        provider.cachedToken = "valid-cached-token"
        provider.tokenExpiresAtMs = System.currentTimeMillis() + 300_000L

        expect:
        provider.getValidTokenMono().block() == "valid-cached-token"
    }

    def "getValidTokenMono refreshes token asynchronously via HttpClient"() {
        given:
        def mockClient = new FakeHttpClient()
        def response = fakeResponse(200, '{"access_token": "new-bearer-token", "expires_in": 7200}')
        HttpRequest capturedRequest = null
        mockClient.sendAsyncClosure = { HttpRequest req, HttpResponse.BodyHandler handler ->
            capturedRequest = req
            CompletableFuture.completedFuture(response)
        }

        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "cid", "csecret", null, "custom-scope", objectMapper, mockClient)

        when:
        def token = provider.getValidTokenMono().block()

        then:
        token == "new-bearer-token"
        provider.cachedToken == "new-bearer-token"
        provider.tokenExpiresAtMs > System.currentTimeMillis()
        capturedRequest != null
        capturedRequest.uri().toString() == "https://example.zendesk.com/oauth/tokens"
        capturedRequest.headers().firstValue("Content-Type").get() == "application/x-www-form-urlencoded"
    }

    def "coalesces concurrent refresh requests into a single flight"() {
        given:
        def mockClient = new FakeHttpClient()
        def response = fakeResponse(200, '{"access_token": "single-flight-token", "expires_in": 3600}')

        def delayedFuture = new CompletableFuture<HttpResponse<String>>()
        int invocationCount = 0

        mockClient.sendAsyncClosure = { HttpRequest req, HttpResponse.BodyHandler handler ->
            invocationCount++
            delayedFuture
        }

        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "cid", "csecret", null, "read", objectMapper, mockClient)

        when:
        def mono1 = provider.getValidTokenMono()
        def mono2 = provider.getValidTokenMono()

        def future1 = mono1.toFuture()
        def future2 = mono2.toFuture()

        // Complete the single in-flight future
        delayedFuture.complete(response)

        def result1 = future1.get(5, TimeUnit.SECONDS)
        def result2 = future2.get(5, TimeUnit.SECONDS)

        then:
        invocationCount == 1
        result1 == "single-flight-token"
        result2 == "single-flight-token"
        provider.cachedToken == "single-flight-token"
    }

    def "clears token and returns empty when refresh receives HTTP error status"() {
        given:
        def mockClient = new FakeHttpClient()
        def response = fakeResponse(401, '{"error": "unauthorized"}')
        mockClient.sendAsyncClosure = { HttpRequest req, HttpResponse.BodyHandler handler ->
            CompletableFuture.completedFuture(response)
        }

        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "cid", "csecret", null, "read", objectMapper, mockClient)
        provider.cachedToken = "old-token"
        provider.tokenExpiresAtMs = System.currentTimeMillis() - 10_000L

        when:
        def result = provider.getValidTokenMono().blockOptional()

        then:
        result.isEmpty()
        provider.cachedToken == null
        provider.tokenExpiresAtMs == 0L
    }

    def "clears token and returns empty when response body is invalid JSON"() {
        given:
        def mockClient = new FakeHttpClient()
        def response = fakeResponse(200, 'not-json-content')
        mockClient.sendAsyncClosure = { HttpRequest req, HttpResponse.BodyHandler handler ->
            CompletableFuture.completedFuture(response)
        }

        def provider = new ZendeskTokenProvider("https://example.zendesk.com", "cid", "csecret", null, "read", objectMapper, mockClient)

        when:
        def result = provider.getValidTokenMono().blockOptional()

        then:
        result.isEmpty()
        provider.cachedToken == null
    }

    def "strips trailing slashes from zendeskUrl during refresh"() {
        given:
        def mockClient = new FakeHttpClient()
        def response = fakeResponse(200, '{"access_token": "token", "expires_in": 3600}')
        HttpRequest capturedRequest = null
        mockClient.sendAsyncClosure = { HttpRequest req, HttpResponse.BodyHandler handler ->
            capturedRequest = req
            CompletableFuture.completedFuture(response)
        }

        def provider = new ZendeskTokenProvider("https://example.zendesk.com///", "cid", "csecret", null, "read", objectMapper, mockClient)

        when:
        provider.getValidTokenMono().block()

        then:
        capturedRequest.uri().toString() == "https://example.zendesk.com/oauth/tokens"
    }
}
