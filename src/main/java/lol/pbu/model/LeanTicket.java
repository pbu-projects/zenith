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
package lol.pbu.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lol.pbu.z4j.model.Ticket;
import lol.pbu.z4j.model.TicketCustomField;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Lean representation of a Zendesk Ticket containing essential queue review fields.
 */
@Serdeable
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeanTicket(
        Long id,
        @Nullable String status,
        @Nullable String priority,
        @Nullable String subject,
        @Nullable @JsonProperty("requester_id") @JsonAlias("requesterId") Long requesterId,
        @Nullable @JsonProperty("assignee_id") @JsonAlias("assigneeId") Long assigneeId,
        @Nullable @JsonProperty("group_id") @JsonAlias("groupId") Long groupId,
        @Nullable List<String> tags,
        @Nullable @JsonProperty("created_at") @JsonAlias("createdAt") ZonedDateTime createdAt,
        @Nullable @JsonProperty("updated_at") @JsonAlias("updatedAt") ZonedDateTime updatedAt,
        @Nullable @JsonProperty("custom_fields") @JsonAlias("customFields") List<TicketCustomField> customFields
) {

    public static LeanTicket fromTicket(Ticket ticket, boolean includeCustomFields) {
        if (ticket == null) {
            return null;
        }
        String status = ticket.getStatus() != null ? ticket.getStatus().getValue() : null;
        String priority = ticket.getPriority() != null ? ticket.getPriority().getValue() : null;

        List<TicketCustomField> filteredCustomFields = null;
        if (includeCustomFields && ticket.getCustomFields() != null) {
            filteredCustomFields = ticket.getCustomFields().stream()
                    .filter(LeanTicket::isCustomFieldPresent)
                    .toList();
        }

        return new LeanTicket(
                ticket.getId(),
                status,
                priority,
                ticket.getSubject(),
                ticket.getRequesterId(),
                ticket.getAssigneeId(),
                ticket.getGroupId(),
                ticket.getTags(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                filteredCustomFields
        );
    }

    private static boolean isCustomFieldPresent(TicketCustomField customField) {
        if (customField == null) {
            return false;
        }
        return switch (customField) {
            case TicketCustomField.Text t -> t.value() != null && !t.value().isBlank();
            case TicketCustomField.Numeric n -> n.value() != null;
            case TicketCustomField.Decimal d -> d.value() != null;
            case TicketCustomField.Checkbox c -> Boolean.TRUE.equals(c.value());
            case TicketCustomField.TagList tags -> tags.value() != null && !tags.value().isEmpty();
            case TicketCustomField.Raw r -> isRawValuePresent(r.value());
        };
    }

    private static boolean isRawValuePresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence str) {
            return !str.toString().isBlank();
        }
        if (value instanceof Collection<?> col) {
            return !col.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Object[] arr) {
            return arr.length > 0;
        }
        return true;
    }
}
