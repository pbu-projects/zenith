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

import java.util.List;

/**
 * Result envelope for view ticket queries with cursor pagination and lean ticket DTOs.
 */
@Serdeable
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ViewTicketsResult(
        List<LeanTicket> tickets,
        @JsonProperty("returned_count") @JsonAlias("returnedCount") int returnedCount,
        @Nullable @JsonProperty("total_count") @JsonAlias("totalCount") Long totalCount,
        @JsonProperty("has_more") @JsonAlias("hasMore") boolean hasMore,
        @Nullable @JsonProperty("next_cursor") @JsonAlias("nextCursor") String nextCursor
) {}
