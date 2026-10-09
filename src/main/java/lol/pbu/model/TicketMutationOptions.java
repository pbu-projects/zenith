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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/**
 * Options encapsulating ticket mutation parameters for single ticket and batch ticket update operations.
 * Avoids telescopic parameter lists and provides a clean, extensible builder.
 */
@Introspected
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
        @Nullable Long ticketFormId,
        @Nullable List<String> additionalTags,
        @Nullable List<String> removeTags,
        @Nullable List<String> tags,
        @Nullable String subject,
        @Nullable List<?> emailCcs,
        @Nullable List<?> followers
) {

    public TicketMutationOptions(
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
            @Nullable Long ticketFormId,
            @Nullable List<String> additionalTags,
            @Nullable List<String> removeTags,
            @Nullable List<String> tags,
            @Nullable String subject
    ) {
        this(comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, additionalTags, removeTags, tags, subject, null, null);
    }

    public TicketMutationOptions(
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
            @Nullable Long ticketFormId,
            @Nullable List<String> additionalTags,
            @Nullable List<String> removeTags,
            @Nullable List<String> tags
    ) {
        this(comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, additionalTags, removeTags, tags, null, null, null);
    }

    public TicketMutationOptions(
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
        this(comment, status, priority, isPublic, uploadTokens, attachmentFilePaths, problemId, convertToIncident, customFields, requesterId, type, customStatusId, ticketFormId, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .comment(comment)
                .status(status)
                .priority(priority)
                .isPublic(isPublic)
                .uploadTokens(uploadTokens)
                .attachmentFilePaths(attachmentFilePaths)
                .problemId(problemId)
                .convertToIncident(convertToIncident)
                .customFields(customFields)
                .requesterId(requesterId)
                .type(type)
                .customStatusId(customStatusId)
                .ticketFormId(ticketFormId)
                .additionalTags(additionalTags)
                .removeTags(removeTags)
                .tags(tags)
                .subject(subject)
                .emailCcs(emailCcs)
                .followers(followers);
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
        private List<String> additionalTags;
        private List<String> removeTags;
        private List<String> tags;
        private String subject;
        private List<?> emailCcs;
        private List<?> followers;

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

        public Builder additionalTags(@Nullable List<String> additionalTags) {
            this.additionalTags = additionalTags;
            return this;
        }

        public Builder removeTags(@Nullable List<String> removeTags) {
            this.removeTags = removeTags;
            return this;
        }

        public Builder tags(@Nullable List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder subject(@Nullable String subject) {
            this.subject = subject;
            return this;
        }

        public Builder emailCcs(@Nullable List<?> emailCcs) {
            this.emailCcs = emailCcs;
            return this;
        }

        public Builder followers(@Nullable List<?> followers) {
            this.followers = followers;
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
                    ticketFormId,
                    additionalTags,
                    removeTags,
                    tags,
                    subject,
                    emailCcs,
                    followers
            );
        }
    }
}
