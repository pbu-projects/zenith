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

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import lol.pbu.z4j.model.EmailCC
import lol.pbu.z4j.model.EmailCCAllOfAction
import lol.pbu.z4j.model.Follower
import spock.lang.Specification
import spock.lang.Unroll

class ToolValidationSupportSpec extends Specification {

    def "validateKnownParameters handles description parameter and hints for tools"() {
        when: "null request or arguments"
        ToolValidationSupport.validateKnownParameters(null, "tool")
        ToolValidationSupport.validateKnownParameters(new CallToolRequest("tool", null), "tool")

        then:
        noExceptionThrown()

        when: "unsupported description parameter on update"
        ToolValidationSupport.validateKnownParameters(
                new CallToolRequest("updateTicket", [description: "desc"]),
                "updateTicket",
                "comment"
        )

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("Ticket description cannot be modified after creation")

        when: "unknown parameter on uploadAttachment"
        ToolValidationSupport.validateKnownParameters(
                new CallToolRequest("uploadAttachment", [badParam: "val"]),
                "uploadAttachment",
                "filePath"
        )

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("Valid parameters for uploadAttachment are 'filePath' and 'filename'")

        when: "unknown parameter on getTicketAudits"
        ToolValidationSupport.validateKnownParameters(
                new CallToolRequest("getTicketAudits", [badParam: "val"]),
                "getTicketAudits",
                "ticketId"
        )

        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message.contains("The only valid parameter for getTicketAudits is 'ticketId'")

        when: "unknown parameter on createTicket"
        ToolValidationSupport.validateKnownParameters(
                new CallToolRequest("createTicket", [badParam: "val"]),
                "createTicket",
                "subject"
        )

        then:
        def e4 = thrown(IllegalArgumentException)
        e4.message.contains("If you meant to set a custom field, use the 'customFields' array parameter")

        when: "unknown parameter on CustomObject tool"
        ToolValidationSupport.validateKnownParameters(
                new CallToolRequest("manageCustomObject", [badParam: "val"]),
                "manageCustomObject",
                "objectKey"
        )

        then:
        def e5 = thrown(IllegalArgumentException)
        e5.message.contains("Check the tool documentation for valid parameters")

        when: "unknown parameter on other tool"
        ToolValidationSupport.validateKnownParameters(
                new CallToolRequest("otherTool", [badParam: "val"]),
                "otherTool",
                "knownParam"
        )

        then:
        def e6 = thrown(IllegalArgumentException)
        e6.message.contains("If you meant to update a custom field, use the 'customFields' array parameter")
    }

    def "resolveCustomStatusId and bounds validation"() {
        expect:
        ToolValidationSupport.resolveCustomStatusId(10L, null) == 10L
        ToolValidationSupport.resolveCustomStatusId(null, new CallToolRequest("t", [custom_status_id: 20L])) == 20L
        ToolValidationSupport.resolveCustomStatusId(null, new CallToolRequest("t", [custom_status_id: "30"])) == 30L
        ToolValidationSupport.resolveCustomStatusId(null, new CallToolRequest("t", [:])) == null
        ToolValidationSupport.resolveCustomStatusId(null, null) == null

        when:
        ToolValidationSupport.resolveCustomStatusId(null, new CallToolRequest("t", [custom_status_id: "abc"]))

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("custom_status_id must be a numeric ID")

        when: "validating bounds"
        ToolValidationSupport.validateCustomStatusBounds(null)
        ToolValidationSupport.validateCustomStatusBounds(100L)

        then:
        noExceptionThrown()

        when: "non-positive customStatusId"
        ToolValidationSupport.validateCustomStatusBounds(0L)

        then:
        thrown(IllegalArgumentException)

        when: "negative customStatusId"
        ToolValidationSupport.validateCustomStatusBounds(-1L)

        then:
        thrown(IllegalArgumentException)
    }

    def "resolveTicketFormId and bounds validation"() {
        expect:
        ToolValidationSupport.resolveTicketFormId(10L, null) == 10L
        ToolValidationSupport.resolveTicketFormId(null, new CallToolRequest("t", [ticket_form_id: 20L])) == 20L
        ToolValidationSupport.resolveTicketFormId(null, new CallToolRequest("t", [ticketFormId: "30"])) == 30L
        ToolValidationSupport.resolveTicketFormId(null, null) == null

        when:
        ToolValidationSupport.resolveTicketFormId(null, new CallToolRequest("t", [ticket_form_id: "invalid"]))

        then:
        thrown(IllegalArgumentException)

        when:
        ToolValidationSupport.validateTicketFormBounds(null)
        ToolValidationSupport.validateTicketFormBounds(5L)

        then:
        noExceptionThrown()

        when:
        ToolValidationSupport.validateTicketFormBounds(0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "resolveAndValidateTags handles collections, arrays, and error cases"() {
        expect:
        ToolValidationSupport.resolveAndValidateTags(["a", "b"], "tags", null, null) == ["a", "b"]
        ToolValidationSupport.resolveAndValidateTags(null, "additionalTags", "additional_tags", new CallToolRequest("t", [additional_tags: ["t1", "t2"]])) == ["t1", "t2"]
        ToolValidationSupport.resolveAndValidateTags(null, "removeTags", "remove_tags", new CallToolRequest("t", [removeTags: ["r1"]])) == ["r1"]
        ToolValidationSupport.resolveAndValidateTags(null, "tags", null, new CallToolRequest("t", [tags: ["x", "y"] as Object[]])) == ["x", "y"]
        ToolValidationSupport.resolveAndValidateTags(null, "tags", null, null) == null

        when: "raw is not collection or array"
        ToolValidationSupport.resolveAndValidateTags(null, "tags", null, new CallToolRequest("t", [tags: "not-a-list"]))

        then:
        thrown(IllegalArgumentException)

        when: "null tag"
        ToolValidationSupport.resolveAndValidateTags([null], "tags", null, null)

        then:
        thrown(IllegalArgumentException)

        when: "blank tag"
        ToolValidationSupport.resolveAndValidateTags(["   "], "tags", null, null)

        then:
        thrown(IllegalArgumentException)

        when: "tag with whitespace"
        ToolValidationSupport.resolveAndValidateTags(["tag with spaces"], "tags", null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "validateEmail and resolveRequesterEmail"() {
        expect:
        ToolValidationSupport.validateEmail(null, "email") == null
        ToolValidationSupport.validateEmail("test@example.com", "email") == "test@example.com"
        ToolValidationSupport.validateEmail("  user@test.org  ", "email") == "user@test.org"
        ToolValidationSupport.resolveRequesterEmail("direct@test.com", null) == "direct@test.com"
        ToolValidationSupport.resolveRequesterEmail(null, new CallToolRequest("t", [requesterEmail: "camel@test.com"])) == "camel@test.com"
        ToolValidationSupport.resolveRequesterEmail(null, new CallToolRequest("t", [requester_email: "snake@test.com"])) == "snake@test.com"
        ToolValidationSupport.resolveRequesterEmail(null, null) == null

        when: "empty email"
        ToolValidationSupport.validateEmail("", "email")

        then:
        thrown(IllegalArgumentException)

        when: "invalid email format"
        ToolValidationSupport.validateEmail("bad-email", "email")

        then:
        thrown(IllegalArgumentException)
    }

    def "resolveRequesterId"() {
        expect:
        ToolValidationSupport.resolveRequesterId(10L, null) == 10L
        ToolValidationSupport.resolveRequesterId(null, new CallToolRequest("t", [requesterId: 20L])) == 20L
        ToolValidationSupport.resolveRequesterId(null, new CallToolRequest("t", [requester_id: "30"])) == 30L
        ToolValidationSupport.resolveRequesterId(null, null) == null

        when: "invalid requester ID"
        ToolValidationSupport.resolveRequesterId(null, new CallToolRequest("t", [requesterId: "abc"]))

        then:
        thrown(IllegalArgumentException)
    }

    def "resolveAndValidateEmailCcs handles diverse representations and errors"() {
        given:
        def existingPut = new EmailCC().setUserEmail("existing@test.com")
        def existingDel = new EmailCC().setUserId("555").setAction(EmailCCAllOfAction.DELETE)

        expect:
        ToolValidationSupport.resolveAndValidateEmailCcs(null, null) == null

        when: "passing EmailCC instances"
        def resCC = ToolValidationSupport.resolveAndValidateEmailCcs([existingPut, existingDel], null)

        then:
        resCC.size() == 2
        resCC[0].userEmail == "existing@test.com"
        resCC[0].action == EmailCCAllOfAction.PUT
        resCC[1].userId == "555"
        resCC[1].action == EmailCCAllOfAction.DELETE

        when: "passing numbers"
        def resNum = ToolValidationSupport.resolveAndValidateEmailCcs([100L, 200], null)

        then:
        resNum.size() == 2
        resNum[0].userId == "100"
        resNum[0].action == EmailCCAllOfAction.PUT
        resNum[1].userId == "200"

        when: "passing strings with email and numeric strings"
        def resStr = ToolValidationSupport.resolveAndValidateEmailCcs(["user@example.com", "300"], null)

        then:
        resStr.size() == 2
        resStr[0].userEmail == "user@example.com"
        resStr[0].action == EmailCCAllOfAction.PUT
        resStr[1].userId == "300"

        when: "passing maps with various key combinations"
        def resMap = ToolValidationSupport.resolveAndValidateEmailCcs([
                [userId: 10L, userEmail: "u1@test.com", userName: "User One", action: "delete"],
                [id: "20", action: EmailCCAllOfAction.PUT],
                [email: "u3@test.com"]
        ], null)

        then:
        resMap.size() == 3
        resMap[0].userId == "10"
        resMap[0].userEmail == "u1@test.com"
        resMap[0].userName == "User One"
        resMap[0].action == EmailCCAllOfAction.DELETE
        resMap[1].userId == "20"
        resMap[1].action == EmailCCAllOfAction.PUT
        resMap[2].userEmail == "u3@test.com"
        resMap[2].action == EmailCCAllOfAction.PUT

        when: "passing snake_case in request"
        def resSnake = ToolValidationSupport.resolveAndValidateEmailCcs(null, new CallToolRequest("t", [email_ccs: ["cc@test.com"]]))

        then:
        resSnake.size() == 1
        resSnake[0].userEmail == "cc@test.com"

        when: "passing array in request"
        def resArr = ToolValidationSupport.resolveAndValidateEmailCcs(null, new CallToolRequest("t", [emailCcs: ["arr@test.com"] as Object[]]))

        then:
        resArr.size() == 1
        resArr[0].userEmail == "arr@test.com"

        when: "raw is not collection or array"
        ToolValidationSupport.resolveAndValidateEmailCcs(null, new CallToolRequest("t", [emailCcs: 12345]))

        then:
        thrown(IllegalArgumentException)

        when: "item is null"
        ToolValidationSupport.resolveAndValidateEmailCcs([null], null)

        then:
        thrown(IllegalArgumentException)

        when: "number is negative or zero"
        ToolValidationSupport.resolveAndValidateEmailCcs([0L], null)

        then:
        thrown(IllegalArgumentException)

        when: "string is empty"
        ToolValidationSupport.resolveAndValidateEmailCcs(["   "], null)

        then:
        thrown(IllegalArgumentException)

        when: "string has invalid email"
        ToolValidationSupport.resolveAndValidateEmailCcs(["bad@email@domain"], null)

        then:
        thrown(IllegalArgumentException)

        when: "string has negative number"
        ToolValidationSupport.resolveAndValidateEmailCcs(["-10"], null)

        then:
        thrown(IllegalArgumentException)

        when: "string is non-numeric and non-email"
        ToolValidationSupport.resolveAndValidateEmailCcs(["not-an-email-or-id"], null)

        then:
        thrown(IllegalArgumentException)

        when: "map has neither user_id nor user_email"
        ToolValidationSupport.resolveAndValidateEmailCcs([[action: "put"]], null)

        then:
        thrown(IllegalArgumentException)

        when: "map has negative user_id"
        ToolValidationSupport.resolveAndValidateEmailCcs([[userId: -5L, userEmail: "a@b.com"]], null)

        then:
        thrown(IllegalArgumentException)

        when: "map has non-numeric string user_id"
        ToolValidationSupport.resolveAndValidateEmailCcs([[userId: "invalid-id", userEmail: "a@b.com"]], null)

        then:
        thrown(IllegalArgumentException)

        when: "map has zero string user_id"
        ToolValidationSupport.resolveAndValidateEmailCcs([[userId: "0", userEmail: "a@b.com"]], null)

        then:
        thrown(IllegalArgumentException)

        when: "map has invalid action"
        ToolValidationSupport.resolveAndValidateEmailCcs([[userId: 1L, action: "unsupported-action"]], null)

        then:
        thrown(IllegalArgumentException)

        when: "item has unsupported type"
        ToolValidationSupport.resolveAndValidateEmailCcs([true], null)

        then:
        thrown(IllegalArgumentException)
    }

    def "resolveAndValidateFollowers handles diverse representations and errors"() {
        given:
        def existingPut = new Follower().setUserEmail("follower@test.com")
        def existingDel = new Follower().setUserId("777").setAction(EmailCCAllOfAction.DELETE)

        expect:
        ToolValidationSupport.resolveAndValidateFollowers(null, null) == null

        when: "passing Follower instances"
        def resFol = ToolValidationSupport.resolveAndValidateFollowers([existingPut, existingDel], null)

        then:
        resFol.size() == 2
        resFol[0].userEmail == "follower@test.com"
        resFol[0].action == EmailCCAllOfAction.PUT
        resFol[1].userId == "777"
        resFol[1].action == EmailCCAllOfAction.DELETE

        when: "passing numbers and strings"
        def resMixed = ToolValidationSupport.resolveAndValidateFollowers([500L, "agent@test.com", "600"], null)

        then:
        resMixed.size() == 3
        resMixed[0].userId == "500"
        resMixed[1].userEmail == "agent@test.com"
        resMixed[2].userId == "600"

        when: "passing maps"
        def resMaps = ToolValidationSupport.resolveAndValidateFollowers([
                [userId: 12L, action: "put"],
                [userEmail: "agent2@test.com"]
        ], null)

        then:
        resMaps.size() == 2
        resMaps[0].userId == "12"
        resMaps[1].userEmail == "agent2@test.com"

        when: "passing array in request"
        def resArr = ToolValidationSupport.resolveAndValidateFollowers(null, new CallToolRequest("t", [followers: ["arr_f@test.com"] as Object[]]))

        then:
        resArr.size() == 1
        resArr[0].userEmail == "arr_f@test.com"

        when: "item is null"
        ToolValidationSupport.resolveAndValidateFollowers([null], null)

        then:
        thrown(IllegalArgumentException)

        when: "number is <= 0"
        ToolValidationSupport.resolveAndValidateFollowers([0L], null)

        then:
        thrown(IllegalArgumentException)

        when: "string is empty"
        ToolValidationSupport.resolveAndValidateFollowers(["   "], null)

        then:
        thrown(IllegalArgumentException)

        when: "string has invalid email"
        ToolValidationSupport.resolveAndValidateFollowers(["bad@@fol.com"], null)

        then:
        thrown(IllegalArgumentException)

        when: "string has non-positive number"
        ToolValidationSupport.resolveAndValidateFollowers(["-50"], null)

        then:
        thrown(IllegalArgumentException)

        when: "string is non-numeric non-email"
        ToolValidationSupport.resolveAndValidateFollowers(["invalid-fol-string"], null)

        then:
        thrown(IllegalArgumentException)

        when: "map has neither user_id nor user_email"
        ToolValidationSupport.resolveAndValidateFollowers([[action: "put"]], null)

        then:
        thrown(IllegalArgumentException)

        when: "item has unsupported type"
        ToolValidationSupport.resolveAndValidateFollowers([true], null)

        then:
        thrown(IllegalArgumentException)
    }
}
