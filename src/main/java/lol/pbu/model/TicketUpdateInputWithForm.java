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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lol.pbu.z4j.model.TicketUpdateInput;

/**
 * Extension of {@link TicketUpdateInput} that includes the optional {@code ticket_form_id}
 * attribute for changing the ticket form during ticket update and batch update operations.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketUpdateInputWithForm extends TicketUpdateInput {

    public static final String JSON_PROPERTY_TICKET_FORM_ID = "ticket_form_id";

    @Nullable
    @JsonProperty(JSON_PROPERTY_TICKET_FORM_ID)
    private Long ticketFormId;

    public TicketUpdateInputWithForm() {
        super();
    }

    public @Nullable Long getTicketFormId() {
        return ticketFormId;
    }

    public TicketUpdateInputWithForm setTicketFormId(@Nullable Long ticketFormId) {
        this.ticketFormId = ticketFormId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TicketUpdateInputWithForm that)) return false;
        if (!super.equals(o)) return false;
        return java.util.Objects.equals(ticketFormId, that.ticketFormId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), ticketFormId);
    }

    @Override
    public String toString() {
        return "TicketUpdateInputWithForm(super=" + super.toString() + ", ticketFormId=" + ticketFormId + ")";
    }
}
