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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/**
 * Options encapsulating ticket mutation parameters for single ticket and batch ticket update operations.
 * Avoids telescopic parameter lists and provides a clean, extensible builder.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketMutationOptions(
        @Nullable String comment,
        @Nullable String status,
        @Nullable String priority,
        @Nullable Boolean isPublic,
        @Nullable List<String> uploadTokens,
        @Nullable List<String> attachmentFilePaths,
        @Nullable Long problemId,
        @Nullable Boolean convertToIncident,
        @Nullable List<Map<String, Object>> customFields,
        @Nullable Long requesterId,
        @Nullable String type,
        @Nullable Long customStatusId,
        @Nullable Long ticketFormId
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String comment;
        private String status;
        private String priority;
        private Boolean isPublic;
        private List<String> uploadTokens;
        private List<String> attachmentFilePaths;
        private Long problemId;
        private Boolean convertToIncident;
        private List<Map<String, Object>> customFields;
        private Long requesterId;
        private String type;
        private Long customStatusId;
        private Long ticketFormId;

        public Builder comment(@Nullable String comment) {
            this.comment = comment;
            return this;
        }

        public Builder status(@Nullable String status) {
            this.status = status;
            return this;
        }

        public Builder priority(@Nullable String priority) {
            this.priority = priority;
            return this;
        }

        public Builder isPublic(@Nullable Boolean isPublic) {
            this.isPublic = isPublic;
            return this;
        }

        public Builder uploadTokens(@Nullable List<String> uploadTokens) {
            this.uploadTokens = uploadTokens;
            return this;
        }

        public Builder attachmentFilePaths(@Nullable List<String> attachmentFilePaths) {
            this.attachmentFilePaths = attachmentFilePaths;
            return this;
        }

        public Builder problemId(@Nullable Long problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder convertToIncident(@Nullable Boolean convertToIncident) {
            this.convertToIncident = convertToIncident;
            return this;
        }

        public Builder customFields(@Nullable List<Map<String, Object>> customFields) {
            this.customFields = customFields;
            return this;
        }

        public Builder requesterId(@Nullable Long requesterId) {
            this.requesterId = requesterId;
            return this;
        }

        public Builder type(@Nullable String type) {
            this.type = type;
            return this;
        }

        public Builder customStatusId(@Nullable Long customStatusId) {
            this.customStatusId = customStatusId;
            return this;
        }

        public Builder ticketFormId(@Nullable Long ticketFormId) {
            this.ticketFormId = ticketFormId;
            return this;
        }

        public TicketMutationOptions build() {
            return new TicketMutationOptions(
                    comment,
                    status,
                    priority,
                    isPublic,
                    uploadTokens,
                    attachmentFilePaths,
                    problemId,
                    convertToIncident,
                    customFields,
                    requesterId,
                    type,
                    customStatusId,
                    ticketFormId
            );
        }
    }
}
