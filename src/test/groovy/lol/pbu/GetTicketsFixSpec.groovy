package lol.pbu

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject
import lol.pbu.tools.ZendeskTools

@MicronautTest
class GetTicketsFixSpec extends Specification {

    @Inject
    ZendeskTools zendeskTools

    void 'getTickets fails loudly when fetching a ticket fails'() {
        when:
        zendeskTools.getTickets([999999999L])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Failed to fetch ticket 999999999:")
    }
}
