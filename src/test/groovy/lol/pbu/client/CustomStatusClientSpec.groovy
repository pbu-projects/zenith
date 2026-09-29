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
package lol.pbu.client

import lol.pbu.model.CustomStatusResponse
import lol.pbu.model.CustomStatusesResponse
import lol.pbu.model.TicketFormStatus
import lol.pbu.model.TicketFormStatusesResponse
import lol.pbu.z4j.model.TicketFieldCustomStatusObject
import reactor.core.publisher.Mono
import spock.lang.Specification

class CustomStatusClientSpec extends Specification {

    def "default listTicketFormStatuses delegates to single-arg overload with null"() {
        given:
        Long capturedId = -1L
        def client = new CustomStatusClient() {
            @Override
            Mono<CustomStatusesResponse> listCustomStatuses(String statusCategories, Boolean active) {
                return Mono.empty()
            }

            @Override
            Mono<CustomStatusResponse> showCustomStatus(Long id) {
                return Mono.empty()
            }

            @Override
            Mono<TicketFormStatusesResponse> listTicketFormStatuses(Long ticketFormId) {
                capturedId = ticketFormId
                return Mono.just(new TicketFormStatusesResponse([new TicketFormStatus("assoc-1", 101L, 1001L)]))
            }
        }

        when: "calling no-arg default method"
        def response = client.listTicketFormStatuses().block()

        then: "delegated to listTicketFormStatuses(null)"
        capturedId == null
        response != null
        response.ticketFormStatuses().size() == 1
        response.ticketFormStatuses()[0].id() == "assoc-1"
    }

    def "CustomStatusResponse record accessors work properly"() {
        given:
        def statusObj = new TicketFieldCustomStatusObject().tap {
            id = 101L
            agentLabel = "Pending Review"
        }

        when:
        def response = new CustomStatusResponse(statusObj)

        then:
        response.customStatus() != null
        response.customStatus().id == 101L
        response.customStatus().agentLabel == "Pending Review"
    }
}
