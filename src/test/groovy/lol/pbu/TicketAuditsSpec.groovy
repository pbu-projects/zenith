package lol.pbu

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import lol.pbu.z4j.model.TicketAuditsResponse
import spock.lang.Specification

@MicronautTest
class TicketAuditsSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    def "issue 60: AutomaticAnswerSend event with array body reads without error"() {
        given:
        String json = '''{
  "audits": [
    {
      "id": 1,
      "ticket_id": 123,
      "events": [
        {
          "id": 0,
          "type": "AutomaticAnswerSend",
          "author_id": -1,
          "body": [
            {
              "article": {
                "article_id": 0,
                "brand_id": 0,
                "locale": "en-us",
                "score": 0.2221,
                "title": "Example article",
                "url": "https://example.zendesk.com/api/v2/help_center/en-us/articles/0.json",
                "html_url": "https://example.zendesk.com/hc/en-us/articles/0-Example-article",
                "id": 0
              },
              "reviews": { "enduser": null, "agent": [] },
              "viewed": false
            }
          ],
          "public": true,
          "audit_id": 0,
          "created_at": "2026-01-01T00:00:00Z"
        }
      ]
    }
  ]
}'''

        when:
        def resp = objectMapper.readValue(json, TicketAuditsResponse)
        def event = resp.audits[0].events[0]

        then:
        resp != null
        resp.audits.size() == 1
        resp.audits[0].events.size() == 1
        event.type == "AutomaticAnswerSend"
        event.body != null
        event.body.contains("Example article")
    }

    def "issue 63: anonymized ticket audits JSON with KnowledgeRequested and OfferedToEvent reads without error"() {
        given:
        InputStream is = getClass().getResourceAsStream("/fixtures/zendesk_getTicketAudits_repro_anonymized.json")
        assert is != null : "Fixture file /fixtures/zendesk_getTicketAudits_repro_anonymized.json not found on classpath"
        String json = is.text

        when:
        def resp = objectMapper.readValue(json, TicketAuditsResponse)
        def krEvent = resp.audits.collectMany { it.events }.find { it.type == "KnowledgeRequested" }
        def offeredEvent = resp.audits.collectMany { it.events }.find { it.type == "OfferedToEvent" }

        then:
        resp != null
        resp.audits.size() == 11
        krEvent != null
        krEvent.body != null
        krEvent.body.contains("Reference article requested")
        offeredEvent != null
    }

    def "audits response with unknown event type and unexpected field shapes deserializes cleanly"() {
        given:
        String json = '''{
  "audits": [
    {
      "id": 999,
      "ticket_id": 456,
      "events": [
        {
          "id": "12345",
          "type": "CustomUnknownEventType",
          "unrecognized_field_a": "value_a",
          "unrecognized_field_b": {"nested": true},
          "unrecognized_list": [1, 2, 3],
          "body": {"dynamic_key": "dynamic_value", "count": 42},
          "value": ["item1", "item2"],
          "previous_value": 100,
          "recipients": ["101", 102, "invalid-recipient"],
          "via": {"channel": "rule", "source": {"rel": "trigger"}},
          "created_at": "2026-03-01T12:00:00Z"
        },
        {
          "id": "invalid-id",
          "type": "AnotherEvent",
          "value": true,
          "previous_value": false,
          "via": "invalid-via-shape"
        }
      ]
    }
  ]
}'''

        when:
        def resp = objectMapper.readValue(json, TicketAuditsResponse)
        def event1 = resp.audits[0].events[0]
        def event2 = resp.audits[0].events[1]

        then:
        resp != null
        resp.audits.size() == 1
        event1.type == "CustomUnknownEventType"
        event1.id == 12345L
        event1.body.contains("dynamic_key")
        event1.value instanceof List
        event1.previousValue == 100
        event1.recipients == [101L, 102L]
        event1.via != null
        event1.via.channel == "rule"

        event2.type == "AnotherEvent"
        event2.id == null
        event2.value == true
        event2.previousValue == false
    }
}
