package lol.pbu.auth

import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

class ZendeskTokenProviderSpec extends Specification {

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
        def provider = new ZendeskTokenProvider("http://localhost:1", "client-id", "client-secret", null, "read", objectMapper)
        provider.cachedToken = "old-expired-token"
        provider.tokenExpiresAtMs = System.currentTimeMillis() - 100_000L

        when:
        def result = provider.getValidToken()

        then:
        result == Optional.empty()
        provider.cachedToken == null
    }
}
