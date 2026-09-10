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

    void 'test zendesk tools against live instance'() {
        when:
        def countResult = zendeskTools.getTicketCount()

        then:
        countResult != null
        countResult.count != null
        countResult.count.value != null

        when:
        def ticketResult = zendeskTools.getTicket(7L)

        then:
        ticketResult != null
        ticketResult.ticket != null
        ticketResult.ticket.id == 7L
    }

}
