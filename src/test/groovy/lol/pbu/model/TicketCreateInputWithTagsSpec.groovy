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

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import lol.pbu.z4j.model.TicketComment
import lol.pbu.z4j.model.TicketUpdateInputPriority
import spock.lang.Specification

import java.util.Map

@MicronautTest
class TicketCreateInputWithTagsSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    def "getter and setter for additionalTags and removeTags operate as expected"() {
        given:
        def input = new TicketCreateInputWithTags()

        expect:
        input.additionalTags == null
        input.removeTags == null

        when:
        input.setAdditionalTags(["tag1", "tag2"])
        input.setRemoveTags(["tag3"])

        then:
        input.additionalTags == ["tag1", "tag2"]
        input.removeTags == ["tag3"]
    }

    def "equals and hashCode contract verification"() {
        given:
        def comment = new TicketComment().tap { body = "Initial" }
        def base = new TicketCreateInputWithTags(comment).tap {
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }
        def same = new TicketCreateInputWithTags(comment).tap {
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }
        def diffAdditional = new TicketCreateInputWithTags(comment).tap {
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["gamma"]
            removeTags = ["beta"]
        }
        def diffRemove = new TicketCreateInputWithTags(comment).tap {
            priority = TicketUpdateInputPriority.HIGH
            additionalTags = ["alpha"]
            removeTags = ["delta"]
        }
        def diffSuper = new TicketCreateInputWithTags(comment).tap {
            priority = TicketUpdateInputPriority.LOW
            additionalTags = ["alpha"]
            removeTags = ["beta"]
        }

        expect:
        base == base
        base == same
        base.hashCode() == same.hashCode()
        base != null
        base != "not a ticket input"
        base != diffAdditional
        base != diffRemove
        base != diffSuper
        base.hashCode() != diffAdditional.hashCode()
        base.hashCode() != diffRemove.hashCode()
    }

    def "toString representation includes tags and super fields"() {
        given:
        def input = new TicketCreateInputWithTags().tap {
            additionalTags = ["tag1"]
            removeTags = ["tag2"]
        }

        when:
        def str = input.toString()

        then:
        str.contains("TicketCreateInputWithTags")
        str.contains("additionalTags=[tag1]")
        str.contains("removeTags=[tag2]")
        str.contains("super=")
    }

    def "serde serialization writes ticket_form_id, custom_status_id, additional_tags, and remove_tags properties"() {
        given:
        def input = new TicketCreateInputWithTags(new TicketComment().tap { body = "Create test" }).tap {
            subject = "Subject test"
            ticketFormId = 1001L
            customStatusId = 101L
            tags = ["main_tag"]
            additionalTags = ["created_tag"]
            removeTags = ["old_tag"]
        }

        when:
        def json = objectMapper.writeValueAsString(input)
        Map<String, Object> map = objectMapper.readValue(json, Map)

        then:
        map.subject == "Subject test"
        map.ticket_form_id == 1001
        map.custom_status_id == 101
        map.tags == ["main_tag"]
        map.additional_tags == ["created_tag"]
        map.remove_tags == ["old_tag"]
        map.comment instanceof Map
        ((Map) map.comment).body == "Create test"
    }
}
