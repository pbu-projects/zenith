package lol.pbu.tools

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ZendeskBatchSizeConfigSpec extends Specification {

    def "Micronaut injects ZENDESK_BATCH_SIZE environment variable into ZendeskTicketTools"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .properties([
                        "ZENDESK_BATCH_SIZE": "35",
                        "micronaut.http.services.zendesk.url": "https://example.zendesk.com"
                ])
                .start()

        when:
        ZendeskTicketTools tools = context.getBean(ZendeskTicketTools)

        then:
        tools != null
        tools.defaultChunkSize == 35

        cleanup:
        context?.close()
    }

    def "Micronaut injects micronaut.http.services.zendesk.batch-size into ZendeskTicketTools"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .properties([
                        "micronaut.http.services.zendesk.batch-size": "45",
                        "micronaut.http.services.zendesk.url": "https://example.zendesk.com"
                ])
                .start()

        when:
        ZendeskTicketTools tools = context.getBean(ZendeskTicketTools)

        then:
        tools != null
        tools.defaultChunkSize == 45

        cleanup:
        context?.close()
    }

    def "ZendeskTicketTools defaults to 100 when batch size is unconfigured"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .properties([
                        "micronaut.http.services.zendesk.url": "https://example.zendesk.com"
                ])
                .start()

        when:
        ZendeskTicketTools tools = context.getBean(ZendeskTicketTools)

        then:
        tools != null
        tools.defaultChunkSize == 100

        cleanup:
        context?.close()
    }

    def "ZendeskTicketTools caps configured batch size at 100"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .properties([
                        "ZENDESK_BATCH_SIZE": "250",
                        "micronaut.http.services.zendesk.url": "https://example.zendesk.com"
                ])
                .start()

        when:
        ZendeskTicketTools tools = context.getBean(ZendeskTicketTools)

        then:
        tools != null
        tools.defaultChunkSize == 100

        cleanup:
        context?.close()
    }
}
