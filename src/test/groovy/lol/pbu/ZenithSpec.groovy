package lol.pbu


import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject

@MicronautTest
class ZenithSpec extends Specification {

    @Inject
    EmbeddedApplication<?> application

    @Inject
    lol.pbu.tools.ZendeskTools zendeskTools

    void 'test it works'() {
        expect:
        application.running
    }

    void 'test zendesk tools if oauth configured'() {
        given:
        def configured = System.getenv("ZENDESK_OAUTH_TOKEN") ||
                (System.getenv("ZENDESK_CLIENT_ID") && System.getenv("ZENDESK_CLIENT_SECRET")) ||
                System.getProperty("micronaut.http.services.zendesk.oauth.token") ||
                (System.getProperty("micronaut.http.services.zendesk.oauth.client-id") && System.getProperty("micronaut.http.services.zendesk.oauth.client-secret"))

        when:
        if (configured) {
            def countResult = zendeskTools.getTicketCount()
            assert countResult != null
            assert countResult.count.value != null

            def ticketResult = zendeskTools.getTicket(7L)
            assert ticketResult != null
            assert ticketResult.ticket != null
            assert ticketResult.ticket.id == 7L
        }

        then:
        noExceptionThrown()
    }

}
