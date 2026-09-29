package lol.pbu

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject
import lol.pbu.tools.ZendeskTicketTools

@MicronautTest
class GetTicketsFixSpec extends Specification {

    @Inject
    ZendeskTicketTools zendeskTicketTools

    void 'getTickets fails loudly when fetching a ticket fails'() {
        when:
        zendeskTicketTools.getTickets([999999999L])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Failed to fetch ticket 999999999:")
    }
}
