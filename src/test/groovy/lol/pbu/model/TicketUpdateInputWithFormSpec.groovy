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
package lol.pbu.model

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import io.micronaut.serde.ObjectMapper
import lol.pbu.z4j.model.TicketComment
import lol.pbu.z4j.model.TicketUpdateInputPriority
import spock.lang.Specification

@MicronautTest
class TicketUpdateInputWithFormSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    def "getter and setter for ticketFormId operate as expected"() {
        given:
        def input = new TicketUpdateInputWithForm()

        expect:
        input.ticketFormId == null

        when:
        input.setTicketFormId(1001L)

        then:
        input.ticketFormId == 1001L
    }

    def "equals and hashCode contract verification"() {
        given:
        def base = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.HIGH
        }
        def same = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.HIGH
        }
        def diffForm = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1002L
            priority = TicketUpdateInputPriority.HIGH
        }
        def diffSuper = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.LOW
        }

        expect:
        base == base
        base == same
        base.hashCode() == same.hashCode()
        base != null
        base != "not a ticket input"
        base != diffForm
        base != diffSuper
        base.hashCode() != diffForm.hashCode()
    }

    def "toString representation includes ticketFormId and super fields"() {
        given:
        def input = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
        }

        when:
        def str = input.toString()

        then:
        str.contains("TicketUpdateInputWithForm")
        str.contains("ticketFormId=1001")
        str.contains("super=")
    }

    def "serde serialization writes ticket_form_id property"() {
        given:
        def input = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            comment = new TicketComment().tap { body = "Test comment" }
        }

        when:
        def json = objectMapper.writeValueAsString(input)

        then:
        json.contains('"ticket_form_id":1001')
        json.contains('"body":"Test comment"')
    }
}
