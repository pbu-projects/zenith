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

import java.util.List;
import java.util.Objects;

/**
 * Extension of {@link TicketUpdateInput} that includes the optional {@code ticket_form_id},
 * {@code additional_tags}, and {@code remove_tags} attributes for ticket update and batch update operations.
 *
 * <p><b>Technical Debt Note:</b> This subclass is a temporary bridge because {@code lol.pbu:z4j}
 * currently lacks {@code ticket_form_id}, {@code additional_tags}, and {@code remove_tags} on the
 * upstream {@link TicketUpdateInput}. Once these are addressed upstream in {@code z4j}, this class
 * can be deprecated and removed in favor of the upstream model.</p>
 */
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketUpdateInputWithForm extends TicketUpdateInput {

    public static final String JSON_PROPERTY_TICKET_FORM_ID = "ticket_form_id";
    public static final String JSON_PROPERTY_ADDITIONAL_TAGS = "additional_tags";
    public static final String JSON_PROPERTY_REMOVE_TAGS = "remove_tags";

    @Nullable
    @JsonProperty(JSON_PROPERTY_TICKET_FORM_ID)
    private Long ticketFormId;

    @Nullable
    @JsonProperty(JSON_PROPERTY_ADDITIONAL_TAGS)
    private List<String> additionalTags;

    @Nullable
    @JsonProperty(JSON_PROPERTY_REMOVE_TAGS)
    private List<String> removeTags;

    public TicketUpdateInputWithForm() {
        super();
    }

    @Override
    public @Nullable Long getTicketFormId() {
        return ticketFormId;
    }

    @Override
    public TicketUpdateInputWithForm setTicketFormId(@Nullable Long ticketFormId) {
        this.ticketFormId = ticketFormId;
        return this;
    }

    public @Nullable List<String> getAdditionalTags() {
        return additionalTags;
    }

    public TicketUpdateInputWithForm setAdditionalTags(@Nullable List<String> additionalTags) {
        this.additionalTags = additionalTags;
        return this;
    }

    public @Nullable List<String> getRemoveTags() {
        return removeTags;
    }

    public TicketUpdateInputWithForm setRemoveTags(@Nullable List<String> removeTags) {
        this.removeTags = removeTags;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TicketUpdateInputWithForm that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(ticketFormId, that.ticketFormId)
                && Objects.equals(additionalTags, that.additionalTags)
                && Objects.equals(removeTags, that.removeTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ticketFormId, additionalTags, removeTags);
    }

    @Override
    public String toString() {
        return "TicketUpdateInputWithForm(super=" + super.toString() + ", ticketFormId=" + ticketFormId
                + ", additionalTags=" + additionalTags + ", removeTags=" + removeTags + ")";
    }
}
