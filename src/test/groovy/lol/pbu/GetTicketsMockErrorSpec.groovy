package lol.pbu

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject
import lol.pbu.tools.ZendeskTools
import lol.pbu.z4j.client.TicketClient
import reactor.core.publisher.Mono

@MicronautTest
class GetTicketsMockErrorSpec extends Specification {

    @Inject
    ZendeskTools zendeskTools

    @Inject
    TicketClient ticketClient

    @MockBean(TicketClient)
    TicketClient ticketClient() {
        Mock(TicketClient)
    }

    void 'getTickets formats error correctly when message is null'() {
        given:
        def exception = new RuntimeException((String) null)
        ticketClient.showTicket(123L) >> Mono.error(exception)

        when:
        zendeskTools.getTickets([123L])

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to fetch ticket 123: [RuntimeException] null"
    }

    void 'getTickets formats error correctly when message is empty'() {
        given:
        def exception = new RuntimeException("")
        ticketClient.showTicket(999L) >> Mono.error(exception)

        when:
        zendeskTools.getTickets([999L])

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to fetch ticket 999: [RuntimeException] "
    }

    void 'getTickets formats error correctly when exception has detailed message'() {
        given:
        def exception = new IllegalArgumentException("Invalid ID format")
        ticketClient.showTicket(456L) >> Mono.error(exception)

        when:
        zendeskTools.getTickets([456L])

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to fetch ticket 456: [IllegalArgumentException] Invalid ID format"
    }
    
    void 'getTickets formats error correctly on switchIfEmpty'() {
        given:
        ticketClient.showTicket(789L) >> Mono.empty()

        when:
        zendeskTools.getTickets([789L])

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to fetch ticket 789: [EmptyResult] Ticket not found"
    }
}
