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

import java.util.Map

@MicronautTest
class TicketUpdateInputWithFormSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    def "getter and setter for ticketFormId and tags operate as expected"() {
        given:
        def input = new TicketUpdateInputWithForm()

        expect:
        input.ticketFormId == null
        input.additionalTags == null
        input.removeTags == null

        when:
        input.setTicketFormId(1001L)
        input.setAdditionalTags(["tag1", "tag2"])
        input.setRemoveTags(["tag3"])

        then:
        input.ticketFormId == 1001L
        input.additionalTags == ["tag1", "tag2"]
        input.removeTags == ["tag3"]
    }

    def "equals and hashCode contract verification"() {
        given:
        def base = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }
        def same = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }
        def diffForm = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1002L
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }
        def diffSuper = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.LOW
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }
        def diffAdditional = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["other"]
            removeTags = ["beta"]
        }
        def diffRemove = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["other"]
        }

        expect:
        base == base
        base == same
        base.hashCode() == same.hashCode()
        base != null
        base != "not a ticket input"
        base != diffForm
        base != diffSuper
        base != diffAdditional
        base != diffRemove
        base.hashCode() != diffForm.hashCode()
        base.hashCode() != diffAdditional.hashCode()
    }

    def "toString representation includes ticketFormId, tags, and super fields"() {
        given:
        def input = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            additionalTags = ["tag1"]
            removeTags = ["tag2"]
        }

        when:
        def str = input.toString()

        then:
        str.contains("TicketUpdateInputWithForm")
        str.contains("ticketFormId=1001")
        str.contains("additionalTags=[tag1]")
        str.contains("removeTags=[tag2]")
        str.contains("super=")
    }

    def "serde serialization writes ticket_form_id, custom_status_id, additional_tags, and remove_tags properties"() {
        given:
        def input = new TicketUpdateInputWithForm().tap {
            ticketFormId = 1001L
            customStatusId = 101L
            additionalTags = ["urgency", "vip"]
            removeTags = ["stale"]
            comment = new TicketComment().tap { body = "Test comment" }
        }

        when:
        def json = objectMapper.writeValueAsString(input)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.ticket_form_id == 1001
        map.custom_status_id == 101
        map.additional_tags == ["urgency", "vip"]
        map.remove_tags == ["stale"]
        map.comment instanceof Map
        ((Map) map.comment).body == "Test comment"
    }
}
