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
package lol.pbu.client;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import lol.pbu.model.CustomStatusResponse;
import lol.pbu.model.CustomStatusesResponse;
import lol.pbu.model.TicketFormStatusesResponse;
import reactor.core.publisher.Mono;

/**
 * Declarative Micronaut HTTP client for Zendesk custom ticket statuses and form associations API endpoints.
 */
@Client("zendesk")
public interface CustomStatusClient {

    @Get("/api/v2/custom_statuses.json{?status_categories,active}")
    Mono<CustomStatusesResponse> listCustomStatuses(
            @QueryValue("status_categories") @Nullable String statusCategories,
            @QueryValue("active") @Nullable Boolean active
    );

    @Get("/api/v2/custom_statuses/{id}.json")
    Mono<CustomStatusResponse> showCustomStatus(@PathVariable("id") Long id);

    @Get("/api/v2/ticket_form_statuses.json{?ticket_form_id}")
    Mono<TicketFormStatusesResponse> listTicketFormStatuses(
            @QueryValue("ticket_form_id") @Nullable Long ticketFormId
    );

    default Mono<TicketFormStatusesResponse> listTicketFormStatuses() {
        return listTicketFormStatuses(null);
    }
}
