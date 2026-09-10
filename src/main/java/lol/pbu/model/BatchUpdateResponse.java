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
import lol.pbu.z4j.model.JobStatus;
import lol.pbu.z4j.model.Ticket;

import java.util.List;

/**
 * Result of a batch ticket update operation, containing either immediate per-ticket results or an async JobStatus.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchUpdateResponse(
        @Nullable @JsonProperty("job_status") JobStatus jobStatus,
        @Nullable @JsonProperty("results") List<TicketUpdateResult> results
) {

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TicketUpdateResult(
            @JsonProperty("ticket_id") Long ticketId,
            @JsonProperty("success") boolean success,
            @Nullable @JsonProperty("ticket") Ticket ticket,
            @Nullable @JsonProperty("error") String error
    ) {}
}
