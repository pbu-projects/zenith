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
package lol.pbu.serde

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import lol.pbu.model.TicketCreateInputWithTags
import lol.pbu.model.TicketUpdateInputWithForm
import lol.pbu.z4j.model.TicketComment
import lol.pbu.z4j.model.TicketCreateInput
import lol.pbu.z4j.model.TicketCreateRequest
import lol.pbu.z4j.model.TicketUpdateInput
import lol.pbu.z4j.model.TicketUpdateRequest
import spock.lang.Specification

import java.util.Map

@MicronautTest
class TicketRequestSerializerSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    def "TicketCreateRequestSerializer serializes request with null ticket"() {
        given:
        def request = new TicketCreateRequest()

        when:
        def json = objectMapper.writeValueAsString(request)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.isEmpty()
    }

    def "TicketCreateRequestSerializer serializes polymorphic TicketCreateInputWithTags preserving tags"() {
        given:
        def input = new TicketCreateInputWithTags(new TicketComment().tap { body = "Hello" }).tap {
            subject = "Issue title"
            additionalTags = ["tag_a", "tag_b"]
            removeTags = ["tag_c"]
        }
        def request = new TicketCreateRequest(input)

        when:
        def json = objectMapper.writeValueAsString(request)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.containsKey("ticket")
        def ticketMap = (Map<String, Object>) map.ticket
        ticketMap.subject == "Issue title"
        ticketMap.additional_tags == ["tag_a", "tag_b"]
        ticketMap.remove_tags == ["tag_c"]
    }

    def "TicketCreateRequestSerializer serializes standard TicketCreateInput"() {
        given:
        def input = new TicketCreateInput(new TicketComment().tap { body = "Standard" }).tap {
            subject = "Standard issue"
        }
        def request = new TicketCreateRequest(input)

        when:
        def json = objectMapper.writeValueAsString(request)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.containsKey("ticket")
        def ticketMap = (Map<String, Object>) map.ticket
        ticketMap.subject == "Standard issue"
    }

    def "TicketUpdateRequestSerializer serializes request with null ticket"() {
        given:
        def request = new TicketUpdateRequest()

        when:
        def json = objectMapper.writeValueAsString(request)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.isEmpty()
    }

    def "TicketUpdateRequestSerializer serializes polymorphic TicketUpdateInputWithForm preserving form and tags"() {
        given:
        def input = new TicketUpdateInputWithForm().tap {
            ticketFormId = 444L
            additionalTags = ["add_x"]
            removeTags = ["rem_y"]
        }
        def request = new TicketUpdateRequest(input)

        when:
        def json = objectMapper.writeValueAsString(request)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.containsKey("ticket")
        def ticketMap = (Map<String, Object>) map.ticket
        ticketMap.ticket_form_id == 444
        ticketMap.additional_tags == ["add_x"]
        ticketMap.remove_tags == ["rem_y"]
    }

    def "TicketUpdateRequestSerializer serializes standard TicketUpdateInput"() {
        given:
        def input = new TicketUpdateInput().tap {
            comment = new TicketComment().tap { body = "Update comment" }
        }
        def request = new TicketUpdateRequest(input)

        when:
        def json = objectMapper.writeValueAsString(request)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.containsKey("ticket")
        def ticketMap = (Map<String, Object>) map.ticket
        ticketMap.comment instanceof Map
        ((Map) ticketMap.comment).body == "Update comment"
    }
}
