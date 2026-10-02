package lol.pbu


import io.micronaut.json.JsonMapper
import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import lol.pbu.tools.ZendeskCustomObjectTools
import lol.pbu.tools.ZendeskMetadataTools
import lol.pbu.tools.ZendeskTicketTools
import spock.lang.Specification

@MicronautTest
class ZenithSpec extends Specification {

    @Inject
    EmbeddedApplication<?> application

    @Inject
    ZendeskTicketTools ticketTools

    @Inject
    ZendeskMetadataTools metadataTools

    @Inject
    ZendeskCustomObjectTools customObjectTools

    void 'test it works'() {
        expect:
        application.running
    }

    void 'test zendesk tools against live instance'() {
        when:
        def countResult = ticketTools.getTicketCount()

        then:
        countResult != null
        countResult.count != null
        countResult.count.value != null

        when:
        def ticketResult = ticketTools.getTicket(7L)

        then:
        ticketResult != null
        ticketResult.ticket != null
        ticketResult.ticket.id == 7L
        ticketResult.ticket.customFields != null
        !ticketResult.ticket.customFields.any { it == null }

        when:
        def ticketsResult = ticketTools.getTickets([7L])

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
        def forms = metadataTools.listTicketForms()

        then:
        forms != null
        forms.ticket_forms != null

        when:
        def customObjects = customObjectTools.listCustomObjects()

        then:
        customObjects != null
        customObjects.customObjects != null

        when:
        def fields = ticketTools.listTicketFields()
        def firstFieldId = fields.ticketFields?.first()?.id
        def field = firstFieldId != null ? ticketTools.getTicketField(firstFieldId) : null

        then:
        firstFieldId == null || (field != null && field.ticketField != null)

        when: "uploading a local file as an attachment"
        File tempFile = File.createTempFile("zenith-upload-test-", ".txt")
        tempFile.text = "Testing Zenith attachment upload from local file"
        def uploadResp = ticketTools.uploadAttachment(tempFile.absolutePath, "zenith-test.txt")

        then:
        uploadResp != null
        uploadResp.upload != null
        uploadResp.upload.token != null
        uploadResp.upload.attachment != null
        uploadResp.upload.attachment.fileName == "zenith-test.txt"

        when: "batch updating tickets concurrently"
        def batchResp = ticketTools.batchUpdateTickets([7L], "Batch concurrent update test", null, null, false, [uploadResp.upload.token], null, false, null, null, null, null, null, null)

        then:
        batchResp != null
        batchResp.results() != null
        !batchResp.results().isEmpty()
        batchResp.results().first().success()

        when: "batch updating tickets asynchronously via Zendesk bulk job"
        def asyncBulkResp = ticketTools.batchUpdateTickets([7L], "Batch bulk async test", null, null, false, null, null, true, null, null, null, null, null, null)

        then:
        asyncBulkResp != null
        asyncBulkResp.jobStatus() != null
        asyncBulkResp.jobStatus().id != null

        when: "fetching job status by id"
        def jobStatusResp = ticketTools.getJobStatus(asyncBulkResp.jobStatus().id)

        then:
        jobStatusResp != null
        jobStatusResp.jobStatus != null
        jobStatusResp.jobStatus.id == asyncBulkResp.jobStatus().id

        cleanup:
        tempFile?.delete()
    }

    @Inject
    JsonMapper jsonMapper
}
