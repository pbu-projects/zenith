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

import lol.pbu.z4j.client.CustomStatusClient
import lol.pbu.z4j.client.TicketFormsClient
import lol.pbu.z4j.model.CustomStatusesResponse
import lol.pbu.z4j.model.TicketFormStatus
import lol.pbu.z4j.model.TicketFormStatusesResponse
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

    def "null client guards return empty collections or null validation"() {
        given:
        def nullService = new ZendeskMetadataService(null, null)

        expect:
        nullService.getCachedCustomStatuses(false) == []
        nullService.getCachedTicketFormStatuses(false) == []
        nullService.getCachedTicketForms(false) == []
        nullService.validateTicketForm(1001L) == null
        nullService.validateCustomStatus(101L, "open") == null
    }

    def "clearCustomStatusCache clears custom status cache and forces refresh"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >>> [
                Mono.just(new CustomStatusesResponse(createSampleCustomStatuses())),
                Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))
        ]

        when:
        def list1 = service.getCachedCustomStatuses(false)
        service.clearCustomStatusCache()
        def list2 = service.getCachedCustomStatuses(false)

        then:
        list1.size() == 4
        list2.size() == 4
        !list1.is(list2)
    }

    def "getCachedCustomStatuses handles null response or null list payload"() {
        when: "response is null"
        customStatusClient.listCustomStatuses(null, null) >> Mono.empty()
        def resNull = service.getCachedCustomStatuses(true)

        then:
        resNull == []

        when: "response has null customStatuses"
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(null))
        def resNullList = service.getCachedCustomStatuses(true)

        then:
        resNullList == []
    }

    def "getCachedTicketFormStatuses caches result and subsequent calls return cached copy"() {
        given:
        customStatusClient.listTicketFormStatuses(null) >>> [
                Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses())),
                Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses()))
        ]

        when: "fetching for first time and re-fetching from cache"
        def first = service.getCachedTicketFormStatuses(false)
        def second = service.getCachedTicketFormStatuses(false)

        then: "cached result is reused"
        first.size() == 3
        second.size() == 3
        first.is(second)

        when: "force refreshing"
        def third = service.getCachedTicketFormStatuses(true)

        then:
        third.size() == 3
    }

    def "getCachedTicketFormStatuses handles null response or null list payload"() {
        when: "response is null"
        customStatusClient.listTicketFormStatuses(null) >> Mono.empty()
        def resNull = service.getCachedTicketFormStatuses(true)

        then:
        resNull == []

        when: "response has null list"
        customStatusClient.listTicketFormStatuses(null) >> Mono.just(new TicketFormStatusesResponse(null))
        def resNullList = service.getCachedTicketFormStatuses(true)

        then:
        resNullList == []
    }

    def "getCachedTicketFormStatuses returns stale cache when client fails after successful fetch"() {
        given:
        customStatusClient.listTicketFormStatuses(null) >>> [
                Mono.just(new TicketFormStatusesResponse(createSampleTicketFormStatuses())),
                Mono.error(new RuntimeException("Flaky network"))
        ]
        service.getCachedTicketFormStatuses(false)

        when: "force refresh errors"
        def stale = service.getCachedTicketFormStatuses(true)

        then:
        stale.size() == 3
    }

    def "getCachedTicketFormStatuses returns empty list when client fails and no cache exists"() {
        given:
        customStatusClient.listTicketFormStatuses(null) >> Mono.error(new RuntimeException("Permanent error"))

        when:
        def res = service.getCachedTicketFormStatuses(false)

        then:
        res == []
    }

    def "getCachedTicketForms handles null response or null list payload"() {
        when: "response is null"
        ticketFormsClient.listTicketForms() >> Mono.empty()
        def resNull = service.getCachedTicketForms(true)

        then:
        resNull == []

        when: "response has null list"
        ticketFormsClient.listTicketForms() >> Mono.just(new TicketFormsResponse(null))
        def resNullList = service.getCachedTicketForms(true)

        then:
        resNullList == []
    }

    def "getCachedTicketForms returns stale cache when client fails after successful fetch"() {
        given:
        ticketFormsClient.listTicketForms() >>> [
                Mono.just(new TicketFormsResponse(createSampleTicketForms())),
                Mono.error(new RuntimeException("API error"))
        ]
        service.getCachedTicketForms(false)

        when: "force refresh errors"
        def stale = service.getCachedTicketForms(true)

        then:
        stale.size() == 3
    }

    def "getCachedTicketForms returns empty list when client fails and no cache exists"() {
        given:
        ticketFormsClient.listTicketForms() >> Mono.error(new RuntimeException("API error"))

        when:
        def res = service.getCachedTicketForms(false)

        then:
        res == []
    }

    def "validateTicketForm returns null when forms catalog is empty"() {
        given:
        ticketFormsClient.listTicketForms() >> Mono.just(new TicketFormsResponse([]))

        expect:
        service.validateTicketForm(1001L) == null
    }

    def "validateTicketForm throws when form not found and no active forms exist"() {
        given:
        def inactiveOnly = [
                new TicketForm().tap {
                    id = 99L
                    name = "Inactive"
                    active = false
                }
        ]
        ticketFormsClient.listTicketForms() >> Mono.just(new TicketFormsResponse(inactiveOnly))

        when:
        service.validateTicketForm(1001L)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Active ticket forms are: none")
    }

    def "validateCustomStatus throws when custom statuses catalog is empty"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse([]))

        when:
        service.validateCustomStatus(101L, "open")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("No custom statuses found for this Zendesk account")
    }

    def "validateCustomStatus throws when status not found and no active statuses exist"() {
        given:
        def inactiveOnly = [
                new TicketFieldCustomStatusObject().tap {
                    id = 99L
                    agentLabel = "Old Inactive"
                    active = false
                    statusCategory = TicketFieldCustomStatusObjectStatusCategory.OPEN
                }
        ]
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(inactiveOnly))

        when:
        service.validateCustomStatus(101L, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Active custom statuses are: none")
    }

    def "validateCustomStatus succeeds with null or blank targetStatus"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        expect:
        service.validateCustomStatus(101L, null).id == 101L
        service.validateCustomStatus(101L, "").id == 101L
    }

    def "validateCustomStatus throws with category mismatch when no active statuses exist in target category"() {
        given:
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse(createSampleCustomStatuses()))

        when:
        service.validateCustomStatus(101L, "solved")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Valid active custom statuses for 'solved' are: none")
    }

    def "validateTicketCustomStatusCategoryMatch handles null inputs and null categories"() {
        given:
        def sampleStatus = createSampleCustomStatuses()[0]
        def sampleTicket = new Ticket().tap {
            id = 123L
            status = TicketStatus.OPEN
        }

        expect:
        service.validateTicketCustomStatusCategoryMatch(123L, null, sampleStatus, 101L) == null
        service.validateTicketCustomStatusCategoryMatch(123L, sampleTicket, null, 101L) == null
        service.validateTicketCustomStatusCategoryMatch(123L, new Ticket().tap { id = 123L }, sampleStatus, 101L) == null
        service.validateTicketCustomStatusCategoryMatch(123L, sampleTicket, new TicketFieldCustomStatusObject().tap { id = 101L }, 101L) == null
    }

    def "validateCustomStatusForForm handles null inputs"() {
        given:
        def sampleStatus = createSampleCustomStatuses()[0]

        when:
        service.validateCustomStatusForForm(null, 1001L)
        service.validateCustomStatusForForm(sampleStatus, null)

        then:
        notThrown(Exception)
    }

    def "validateCustomStatusForForm handles empty form statuses catalog"() {
        given:
        def sampleStatus = createSampleCustomStatuses()[0]
        customStatusClient.listTicketFormStatuses(null) >> Mono.just(new TicketFormStatusesResponse([]))

        when:
        service.validateCustomStatusForForm(sampleStatus, 1001L)

        then:
        notThrown(Exception)
    }

    def "validateCustomStatusForForm handles form without specific statuses configured"() {
        given:
        def sampleStatus = createSampleCustomStatuses()[0]
        def statusesForOtherFormOnly = [new TicketFormStatus("assoc-99", 101L, 9999L)]
        customStatusClient.listTicketFormStatuses(null) >> Mono.just(new TicketFormStatusesResponse(statusesForOtherFormOnly))

        when:
        service.validateCustomStatusForForm(sampleStatus, 1001L)

        then:
        notThrown(Exception)
    }

    def "validateCustomStatusForForm throws when status is not allowed and status has null category"() {
        given:
        def statusNoCat = new TicketFieldCustomStatusObject().tap {
            id = 200L
            agentLabel = "No Category Status"
            active = true
            isDefault = false
        }
        customStatusClient.listCustomStatuses(null, null) >> Mono.just(new CustomStatusesResponse([statusNoCat]))
        customStatusClient.listTicketFormStatuses(null) >> Mono.just(new TicketFormStatusesResponse([
                new TicketFormStatus("assoc-1", 101L, 1001L)
        ]))

        when:
        service.validateCustomStatusForForm(statusNoCat, 1001L)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Custom status ID 200 ('No Category Status') cannot be used with ticket form #1001")
        !e.message.contains("under category")
    }
}
