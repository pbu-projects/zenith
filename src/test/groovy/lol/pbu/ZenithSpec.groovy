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

        when:
        def ticketsResult = zendeskTools.getTickets([7L])

        then:
        ticketsResult != null
        ticketsResult.tickets != null
        !ticketsResult.tickets.isEmpty()
        ticketsResult.tickets.first().id == 7L

        when:
        def json = jsonMapper.writeValueAsString(ticketsResult)

        then:
        json != null
        noExceptionThrown()

        when:
        def forms = zendeskTools.listTicketForms()

        then:
        forms != null
        forms.ticketForms != null

        when:
        def customObjects = zendeskTools.listCustomObjects()

        then:
        customObjects != null
        customObjects.customObjects != null

        when:
        def fields = zendeskTools.listTicketFields()
        def firstFieldId = fields.ticketFields?.first()?.id
        def field = firstFieldId != null ? zendeskTools.getTicketField(firstFieldId) : null

        then:
        firstFieldId == null || (field != null && field.ticketField != null)
    }

    @Inject
    io.micronaut.json.JsonMapper jsonMapper
}
