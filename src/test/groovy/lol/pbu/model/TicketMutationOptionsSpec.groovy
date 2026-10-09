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

import lol.pbu.z4j.model.EmailCC
import lol.pbu.z4j.model.EmailCCAllOfAction
import lol.pbu.z4j.model.Follower
import spock.lang.Specification

class TicketMutationOptionsSpec extends Specification {

    def "all constructor overloads and builders initialize properties properly"() {
        given:
        def emailCc = new EmailCC().setUserEmail("cc@example.com").setAction(EmailCCAllOfAction.PUT)
        def follower = new Follower().setUserId("123").setAction(EmailCCAllOfAction.PUT)

        when: "using full 19-arg constructor"
        def full = new TicketMutationOptions(
                "comment", "open", "high", true, ["tok1"], ["/path"], 10L, false,
                [[id: 1L, value: "val"]], 20L, "incident", 30L, 40L,
                ["add1"], ["rem1"], ["tag1"], "subject", [emailCc], [follower]
        )

        then:
        full.comment() == "comment"
        full.status() == "open"
        full.priority() == "high"
        full.isPublic()
        full.uploadTokens() == ["tok1"]
        full.attachmentFilePaths() == ["/path"]
        full.problemId() == 10L
        !full.convertToIncident()
        full.customFields().size() == 1
        full.requesterId() == 20L
        full.type() == "incident"
        full.customStatusId() == 30L
        full.ticketFormId() == 40L
        full.additionalTags() == ["add1"]
        full.removeTags() == ["rem1"]
        full.tags() == ["tag1"]
        full.subject() == "subject"
        full.emailCcs() == [emailCc]
        full.followers() == [follower]

        when: "using 17-arg constructor overload"
        def c17 = new TicketMutationOptions(
                "c", "s", "p", true, null, null, 1L, false,
                null, 2L, "problem", 3L, 4L, ["a"], ["r"], ["t"], "subj"
        )

        then:
        c17.subject() == "subj"
        c17.emailCcs() == null
        c17.followers() == null

        when: "using 16-arg constructor overload"
        def c16 = new TicketMutationOptions(
                "c", "s", "p", true, null, null, 1L, false,
                null, 2L, "problem", 3L, 4L, ["a"], ["r"], ["t"]
        )

        then:
        c16.subject() == null
        c16.additionalTags() == ["a"]

        when: "using 13-arg constructor overload"
        def c13 = new TicketMutationOptions(
                "c", "s", "p", true, null, null, 1L, false,
                null, 2L, "problem", 3L, 4L
        )

        then:
        c13.ticketFormId() == 4L
        c13.additionalTags() == null

        when: "using toBuilder"
        def rebuilt = full.toBuilder()
                .subject("updated-subject")
                .emailCcs(null)
                .build()

        then:
        rebuilt.subject() == "updated-subject"
        rebuilt.comment() == "comment"
        rebuilt.emailCcs() == null
        rebuilt.followers() == [follower]
    }
}
