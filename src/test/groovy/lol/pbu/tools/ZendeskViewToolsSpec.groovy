/*
 * Copyright 2026 Peanut Butter Unicorn, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lol.pbu.tools

import io.micronaut.serde.ObjectMapper
import lol.pbu.model.LeanTicket
import lol.pbu.model.ViewTicketsResult
import lol.pbu.z4j.client.ViewClient
import lol.pbu.z4j.model.Meta
import lol.pbu.z4j.model.Ticket
import lol.pbu.z4j.model.TicketCustomField
import lol.pbu.z4j.model.TicketPriority
import lol.pbu.z4j.model.TicketStatus
import lol.pbu.z4j.model.TicketsResponse
import lol.pbu.z4j.model.ViewCount
import lol.pbu.z4j.model.ViewCountResponse
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.ZoneOffset
import java.time.ZonedDateTime

class ZendeskViewToolsSpec extends Specification {

    ViewClient viewClient = Mock()
    ZendeskViewTools viewTools = new ZendeskViewTools(viewClient)
    ObjectMapper objectMapper = ObjectMapper.getDefault()

    def "getViewTickets requires non-null viewId"() {
        when:
        viewTools.getViewTickets(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "viewId is required"
    }

    def "getViewTickets(viewId) returns ViewTicketsResult with lean tickets and pagination metadata"() {
        given:
        def now = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZoneOffset.UTC)
        def ticket = new Ticket().tap {
            id = 100L
            subject = "Issue in queue"
            status = TicketStatus.OPEN
            priority = TicketPriority.HIGH
            requesterId = 200L
            assigneeId = 300L
            groupId = 400L
            tags = ["urgent", "vip"]
            createdAt = now
            updatedAt = now
            customFields = [
                    new TicketCustomField.Raw(1L, "some-field-value")
            ]
        }
        def meta = new Meta().tap {
            hasMore = true
            afterCursor = "cursor-token-123"
        }
        def response = new TicketsResponse([ticket], meta, null, 42)

        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(response)
        viewClient.countView(50L) >> Mono.empty()

        when:
        def result = viewTools.getViewTickets(50L).block()

        then:
        result != null
        result.returnedCount() == 1
        result.totalCount() == 42L
        result.hasMore()
        result.nextCursor() == "cursor-token-123"

        and: "ticket is projected to LeanTicket with omitted customFields by default and preserves temporal types"
        result.tickets().size() == 1
        def lean = result.tickets()[0]
        lean.id() == 100L
        lean.subject() == "Issue in queue"
        lean.status() == "open"
        lean.priority() == "high"
        lean.requesterId() == 200L
        lean.assigneeId() == 300L
        lean.groupId() == 400L
        lean.tags() == ["urgent", "vip"]
        lean.createdAt() == now
        lean.updatedAt() == now
        lean.customFields() == null
    }

    def "getViewTickets sideloads totalCount via countView when response.count is null"() {
        given:
        def ticket = new Ticket().tap { id = 101L; subject = "No count in response" }
        def meta = new Meta().tap { hasMore = true; afterCursor = "cur-1" }
        def response = new TicketsResponse([ticket], meta, null, null)

        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(response)
        viewClient.countView(50L) >> Mono.just(new ViewCountResponse().tap {
            viewCount = new ViewCount().tap { value = 104L }
        })

        when:
        def result = viewTools.getViewTickets(50L).block()

        then:
        result != null
        result.returnedCount() == 1
        result.totalCount() == 104L
        result.hasMore()
        result.nextCursor() == "cur-1"
    }

    def "getViewTickets filters out empty/null custom fields and unchecked checkboxes via pattern matching"() {
        given:
        def ticket = new Ticket().tap {
            id = 101L
            subject = "Custom fields ticket"
            customFields = [
                    new TicketCustomField.Text(1L, "valid text"),
                    new TicketCustomField.Text(2L, "   "),
                    new TicketCustomField.Text(3L, null),
                    new TicketCustomField.Checkbox(4L, false), // unchecked must be pruned!
                    new TicketCustomField.Checkbox(5L, true),  // checked must be retained!
                    new TicketCustomField.Numeric(6L, 123L),
                    new TicketCustomField.Numeric(7L, null),
                    new TicketCustomField.Decimal(8L, 45.67f),
                    new TicketCustomField.TagList(9L, ["t1", "t2"]),
                    new TicketCustomField.TagList(10L, []),
                    new TicketCustomField.Raw(11L, null),
                    new TicketCustomField.Raw(12L, "raw text")
            ]
        }
        def response = new TicketsResponse([ticket], new Meta(), null, 1)
        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(response)
        viewClient.countView(50L) >> Mono.empty()

        when:
        def result = viewTools.getViewTickets(50L, null, null, false, null, true).block()

        then:
        result != null
        result.tickets().size() == 1
        def customFields = result.tickets()[0].customFields()
        customFields != null
        customFields*.id() == [1L, 5L, 6L, 8L, 9L, 12L]
        customFields[0].value() == "valid text"
        customFields[1].value() == true
        customFields[2].value() == 123L
        customFields[3].value() == 45.67f
        customFields[4].value() == ["t1", "t2"]
        customFields[5].value() == "raw text"
    }

    def "getViewTickets correctly forwards cursor and pageSize parameters"() {
        given:
        def response = new TicketsResponse([], new Meta(), null, 0)
        viewClient.listTicketsForView(50L, "cursor-abc", 50) >> Mono.just(response)

        when:
        def result = viewTools.getViewTickets(50L, "cursor-abc", 50, false, null, false).block()

        then:
        result != null
        result.returnedCount() == 0
        !result.hasMore()
    }

    def "getViewTickets clamps pageSize between 1 and 100"() {
        given:
        def response = new TicketsResponse([], new Meta(), null, 0)
        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(response)
        viewClient.countView(50L) >> Mono.empty()

        when: "pageSize is over 100"
        def result = viewTools.getViewTickets(50L, null, 250, false, null, false).block()

        then:
        result != null

        when: "pageSize is null or negative"
        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(response)
        viewClient.countView(50L) >> Mono.empty()
        def resultZero = viewTools.getViewTickets(50L, null, 0, false, null, false).block()

        then:
        resultZero != null
    }

    def "getViewTickets auto-pages multiple pages up to end of results"() {
        given:
        def t1 = new Ticket().tap { id = 1L; subject = "Ticket 1" }
        def t2 = new Ticket().tap { id = 2L; subject = "Ticket 2" }
        def t3 = new Ticket().tap { id = 3L; subject = "Ticket 3" }

        def metaPage1 = new Meta().tap {
            hasMore = true
            afterCursor = "page2-cursor"
        }
        def metaPage2 = new Meta().tap {
            hasMore = false
            afterCursor = null
        }

        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(new TicketsResponse([t1, t2], metaPage1, null, 3))
        viewClient.countView(50L) >> Mono.empty()
        viewClient.listTicketsForView(50L, "page2-cursor", 100) >> Mono.just(new TicketsResponse([t3], metaPage2, null, 3))

        when:
        def result = viewTools.getViewTickets(50L, null, 100, true, null, false).block()

        then:
        result != null
        result.returnedCount() == 3
        result.totalCount() == 3L
        !result.hasMore()
        result.nextCursor() == null
        result.tickets()*.id() == [1L, 2L, 3L]
    }

    def "getViewTickets auto-pages and respects custom maxResults parameter"() {
        given:
        List<Ticket> page1Tickets = (1..100).collect { i -> new Ticket().tap { id = i as Long; subject = "T" + i } }
        List<Ticket> page2Tickets = (101..200).collect { i -> new Ticket().tap { id = i as Long; subject = "T" + i } }

        def metaPage1 = new Meta().tap { hasMore = true; afterCursor = "cur-p2" }
        def metaPage2 = new Meta().tap { hasMore = true; afterCursor = "cur-p3" }

        viewClient.listTicketsForView(50L, null, 100) >> Mono.just(new TicketsResponse(page1Tickets, metaPage1, null, 500))
        viewClient.countView(50L) >> Mono.empty()
        viewClient.listTicketsForView(50L, "cur-p2", 100) >> Mono.just(new TicketsResponse(page2Tickets, metaPage2, null, 500))

        when: "maxResults is set to 200"
        def result = viewTools.getViewTickets(50L, null, 100, true, 200, false).block()

        then:
        result != null
        result.returnedCount() == 200
        result.totalCount() == 500L
        result.hasMore()
        result.nextCursor() == "cur-p3"
    }

    def "ViewTicketsResult serializes to JSON omitting null customFields"() {
        given:
        def now = ZonedDateTime.of(2026, 10, 8, 0, 0, 0, 0, ZoneOffset.UTC)
        def lean = new LeanTicket(
                10L,
                "open",
                "normal",
                "Test Subject",
                20L,
                30L,
                40L,
                ["sample"],
                now,
                now,
                null
        )
        def envelope = new ViewTicketsResult([lean], 1, 100L, true, "next-cur")

        when:
        def json = objectMapper.writeValueAsString(envelope)

        then:
        json.contains('"returned_count":1')
        json.contains('"total_count":100')
        json.contains('"has_more":true')
        json.contains('"next_cursor":"next-cur"')
        json.contains('"tickets"')
        !json.contains('"custom_fields"')

        when:
        def parsed = objectMapper.readValue(json, ViewTicketsResult)

        then:
        parsed != null
        parsed.returnedCount() == 1
        parsed.totalCount() == 100L
        parsed.hasMore()
        parsed.nextCursor() == "next-cur"
        parsed.tickets().size() == 1
        parsed.tickets()[0].id() == 10L
        parsed.tickets()[0].subject() == "Test Subject"
        parsed.tickets()[0].customFields() == null
    }
}
