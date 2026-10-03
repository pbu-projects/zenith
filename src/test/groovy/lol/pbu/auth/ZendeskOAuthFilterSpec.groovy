package lol.pbu.auth

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import reactor.core.publisher.Mono
import spock.lang.Specification

class ZendeskOAuthFilterSpec extends Specification {

    static class TestTokenProvider extends ZendeskTokenProvider {
        Mono<String> tokenMono = Mono.empty()

        TestTokenProvider() {
            super("https://example.com", "", "", null, "", null, null)
        }

        @Override
        Mono<String> getValidTokenMono() {
            return tokenMono
        }
    }

    TestTokenProvider tokenProvider = new TestTokenProvider()
    ZendeskOAuthFilter filter = new ZendeskOAuthFilter(tokenProvider)

    def "attaches bearer token and default json headers when valid token is present"() {
        given:
        tokenProvider.tokenMono = Mono.just("valid-zendesk-bearer-token")
        MutableHttpRequest<?> request = HttpRequest.GET("/api/v2/tickets.json")

        when:
        MutableHttpRequest<?> result = Mono.from(filter.doFilter(request)).block()

        then:
        result != null
        result.getHeaders().get("Authorization") == "Bearer valid-zendesk-bearer-token"
        result.getHeaders().get("Content-Type") == "application/json"
        result.getHeaders().get("Accept") == "application/json"
    }

    def "does not attach bearer token when token provider returns empty"() {
        given:
        tokenProvider.tokenMono = Mono.empty()
        MutableHttpRequest<?> request = HttpRequest.GET("/api/v2/tickets.json")

        when:
        MutableHttpRequest<?> result = Mono.from(filter.doFilter(request)).block()

        then:
        result != null
        result.getHeaders().get("Authorization") == null
        result.getHeaders().get("Content-Type") == "application/json"
        result.getHeaders().get("Accept") == "application/json"
    }

    def "preserves custom content-type header when already set"() {
        given:
        tokenProvider.tokenMono = Mono.just("token-123")
        MutableHttpRequest<?> request = HttpRequest.POST("/api/v2/uploads.json", "dummy data")
                .header("Content-Type", "application/binary")

        when:
        MutableHttpRequest<?> result = Mono.from(filter.doFilter(request)).block()

        then:
        result != null
        result.getHeaders().get("Authorization") == "Bearer token-123"
        result.getHeaders().get("Content-Type") == "application/binary"
        result.getHeaders().get("Accept") == "application/json"
    }
}
