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
package lol.pbu.service

import lol.pbu.client.CustomStatusClient
import lol.pbu.model.CustomStatusesResponse
import lol.pbu.model.TicketFormStatus
import lol.pbu.model.TicketFormStatusesResponse
import lol.pbu.z4j.client.TicketFormsClient
import lol.pbu.z4j.model.Ticket
import lol.pbu.z4j.model.TicketFieldCustomStatusObject
import lol.pbu.z4j.model.TicketFieldCustomStatusObjectStatusCategory
import lol.pbu.z4j.model.TicketForm
import lol.pbu.z4j.model.TicketFormsResponse
import lol.pbu.z4j.model.TicketStatus
import reactor.core.publisher.Mono
import spock.lang.Specification

class ZendeskMetadataServiceSpec extends Specification {

    CustomStatusClient customStatusClient = Mock()
    TicketFormsClient ticketFormsClient = Mock()

    ZendeskMetadataService service = new ZendeskMetadataService(customStatusClient, ticketFormsClient)

    List<TicketFieldCustomStatusObject> createSampleCustomStatuses() {
        return [
                new TicketFieldCustomStatusObject().tap {
                    id = 101L
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.OPEN
                    agentLabel = "Investigating"
                    active = true
                    isDefault = false
                },
                new TicketFieldCustomStatusObject().tap {
                    id = 102L
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.PENDING
                    agentLabel = "Waiting on Customer"
                    active = true
                    isDefault = false
                },
                new TicketFieldCustomStatusObject().tap {
                    id = 103L
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.OPEN
                    agentLabel = "Open Default"
                    active = true
                    isDefault = true
                },
                new TicketFieldCustomStatusObject().tap {
                    id = 104L
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.OPEN
                    agentLabel = "Inactive Status"
                    active = false
                    isDefault = false
                }
        ]
    }

    List<TicketForm> createSampleTicketForms() {
        return [
                new TicketForm().tap {
                    id = 1001L
                    name = "Standard Support Form"
                    active = true
                    defaultForm = true
                },
                new TicketForm().tap {
                    id = 1002L
                    name = "Engineering Bug Form"
                    active = true
                    defaultForm = false
                },
                new TicketForm().tap {
                    id = 1003L
                    name = "Deprecated Form"
                    active = false
                    defaultForm = false
                }
        ]
    }

    List<TicketFormStatus> createSampleTicketFormStatuses() {
        return [
                new TicketFormStatus("assoc-1", 101L, 1001L),
                new TicketFormStatus("assoc-2", 101L, 1002L),
                new TicketFormStatus("assoc-3", 102L, 1001L),
        ]
    }

    def "getCachedCustomStatuses caches result on successful fetch"() {
        when: "fetching for the first time"
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        def list1 = service.getCachedCustomStatuses(false)
        def list2 = service.getCachedCustomStatuses(false)

        then: "subsequent call returns cached list"
        list1.size() == 4
        list2.size() == 4
        list1.is(list2)
    }

    def "getCachedCustomStatuses throws exception when client fails and no cache exists"() {
        when: "client fails and cache is empty"
        customStatusClient.listCustomStatuses(null, null) >> Mono.error(new RuntimeException("API error"))
        service.getCachedCustomStatuses(false)

        then: "throws exception"
        def e = thrown(IllegalArgumentException)
        e.message.contains("Failed to fetch custom statuses from Zendesk")
    }

    def "getCachedCustomStatuses returns stale cache when client fails on refresh"() {
        given: "cache is populated"
        customStatusClient.listCustomStatuses(null, null) >>> [
                Mono.just(new CustomStatusesResponse(createSampleCustomStatuses())),
                Mono.error(new RuntimeException("API 503"))
        ]
        service.getCachedCustomStatuses(false)

        when: "force refreshing when client errors"
        def stale = service.getCachedCustomStatuses(true)

        then: "stale cache is used"
        stale.size() == 4
    }

    def "getCachedTicketForms caches result and clearTicketFormCache invalidates"() {
        when: "fetching ticket forms"
        ticketFormsClient.listTicketForms() >>> [
                Mono.just(new TicketFormsResponse(createSampleTicketForms())),
                Mono.just(new TicketFormsResponse(createSampleTicketForms()))
        ]
        def forms1 = service.getCachedTicketForms(false)
        def forms2 = service.getCachedTicketForms(false)

        then:
        forms1.size() == 3
        forms2.size() == 3

        when: "clearing ticket form cache"
        service.clearTicketFormCache()
        def forms3 = service.getCachedTicketForms(false)

        then:
        forms3.size() == 3
    }

    def "clearAllCaches invalidates both custom statuses and ticket forms"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >>> [
                Mono.just(new CustomStatusesResponse(createSampleCustomStatuses())),
                Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        ]
        ticketFormsClient.listTicketForms() >>> [
                Mono.just(new TicketFormsResponse(createSampleTicketForms())),
                Mono.just(new TicketFormsResponse(createSampleTicketForms()))
        ]
        service.getCachedCustomStatuses(false)
        service.getCachedTicketForms(false)

        when:
        service.clearAllCaches()
        def resStatus = service.getCachedCustomStatuses(false)
        def resForms = service.getCachedTicketForms(false)

        then:
        resStatus.size() == 4
        resForms.size() == 3
    }

    def "validateTicketForm validates presence and active status"() {
        given:
        ticketFormsClient.listTicketForms() >> Mono.just(new TicketFormsResponse(createSampleTicketForms()))

        expect:
        service.validateTicketForm(null) == null
        service.validateTicketForm(1001L).name == "Standard Support Form"

        when: "form does not exist"
        service.validateTicketForm(9999L)
        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("Ticket form ID 9999 does not exist")

        when: "form is inactive"
        service.validateTicketForm(1003L)
        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("is inactive")
    }

    def "validateCustomStatus validates category and active status"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        expect:
        service.validateCustomStatus(null, null) == null
        service.validateCustomStatus(101L, "open").agentLabel == "Investigating"

        when: "status is inactive"
        service.validateCustomStatus(104L, null)
        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("is inactive")

        when: "category mismatch"
        service.validateCustomStatus(101L, "solved")
        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("belongs to category 'open', which does not match status 'solved'")
    }

    def "validateCustomStatusForForm validates form-specific custom statuses"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        customStatusClient.listTicketFormStatuses(null) >> Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))

        def status101 = createSampleCustomStatuses()[0] // 101 on 1001 & 1002
        def status102 = createSampleCustomStatuses()[1] // 102 only on 1001
        def defaultStatus = createSampleCustomStatuses()[2] // 103 is default

        when: "default status on any form"
        service.validateCustomStatusForForm(defaultStatus, 1002L)
        then: "always allowed"
        notThrown(Exception)

        when: "status 101 on form 1001"
        service.validateCustomStatusForForm(status101, 1001L)
        then:
        notThrown(Exception)

        when: "status 102 on form 1002 (not allowed)"
        service.validateCustomStatusForForm(status102, 1002L)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("cannot be used with ticket form #1002")
    }

    def "validateTicketCustomStatusCategoryMatch checks current ticket status"() {
        given:
        def status101 = createSampleCustomStatuses()[0] // open
        def ticketOpen = new Ticket().tap {
            id = 555L
            status = TicketStatus.OPEN
        }
        def ticketPending = new Ticket().tap {
            id = 555L
            status = TicketStatus.PENDING
        }

        when: "current ticket is open"
        service.validateTicketCustomStatusCategoryMatch(555L, ticketOpen, status101, 101L)
        then:
        notThrown(Exception)

        when: "current ticket is pending but target custom status is open"
        service.validateTicketCustomStatusCategoryMatch(555L, ticketPending, status101, 101L)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("belongs to category 'open', but ticket #555 currently has status 'pending'")
    }
}
