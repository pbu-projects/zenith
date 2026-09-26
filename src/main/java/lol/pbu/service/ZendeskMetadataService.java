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
package lol.pbu.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lol.pbu.client.CustomStatusClient;
import lol.pbu.model.CustomStatusesResponse;
import lol.pbu.z4j.model.TicketFieldCustomStatusObject;
import lol.pbu.model.TicketFormStatus;
import lol.pbu.model.TicketFormStatusesResponse;
import lol.pbu.z4j.client.TicketFormsClient;
import lol.pbu.z4j.model.Ticket;
import lol.pbu.z4j.model.TicketForm;
import lol.pbu.z4j.model.TicketFormsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Service managing cached Zendesk metadata such as custom ticket statuses, form-status associations,
 * and ticket forms.
 *
 * <p>Uses thread-safe atomic references for lock-free cache reads/invalidations and isolated
 * fine-grained locks per resource type to prevent cache stampedes without cross-resource contention.
 * Remote calls are explicitly scheduled on {@link Schedulers#boundedElastic()} to prevent blocking
 * reactive event loops.</p>
 */
@Singleton
public class ZendeskMetadataService {

    private static final Logger log = LoggerFactory.getLogger(ZendeskMetadataService.class);

    public static final long CUSTOM_STATUS_CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes
    public static final long TICKET_FORM_CACHE_TTL_MS = 5 * 60 * 1000L;   // 5 minutes

    private final CustomStatusClient customStatusClient;
    private final TicketFormsClient ticketFormsClient;

    private final AtomicReference<CacheEntry<List<TicketFieldCustomStatusObject>>> customStatusesCache = new AtomicReference<>();
    private final AtomicReference<CacheEntry<List<TicketFormStatus>>> ticketFormStatusesCache = new AtomicReference<>();
    private final AtomicReference<CacheEntry<List<TicketForm>>> ticketFormsCache = new AtomicReference<>();

    private final ReentrantLock customStatusLock = new ReentrantLock();
    private final ReentrantLock ticketFormStatusLock = new ReentrantLock();
    private final ReentrantLock ticketFormLock = new ReentrantLock();

    private record CacheEntry<T>(T data, long expiresAtMs) {
        boolean isExpired(long now) {
            return now >= expiresAtMs;
        }
    }

    @Inject
    public ZendeskMetadataService(
            @Nullable CustomStatusClient customStatusClient,
            @Nullable TicketFormsClient ticketFormsClient
    ) {
        this.customStatusClient = customStatusClient;
        this.ticketFormsClient = ticketFormsClient;
    }

    public void clearCustomStatusCache() {
        this.customStatusesCache.set(null);
        this.ticketFormStatusesCache.set(null);
    }

    public void clearTicketFormCache() {
        this.ticketFormsCache.set(null);
    }

    public void clearAllCaches() {
        clearCustomStatusCache();
        clearTicketFormCache();
    }

    public List<TicketFieldCustomStatusObject> getCachedCustomStatuses(boolean forceRefresh) {
        if (customStatusClient == null) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        CacheEntry<List<TicketFieldCustomStatusObject>> current = customStatusesCache.get();
        if (!forceRefresh && current != null && !current.isExpired(now)) {
            return current.data();
        }

        customStatusLock.lock();
        try {
            now = System.currentTimeMillis();
            current = customStatusesCache.get();
            if (!forceRefresh && current != null && !current.isExpired(now)) {
                return current.data();
            }

            try {
                Mono<CustomStatusesResponse> mono = customStatusClient.listCustomStatuses(null, null);
                CustomStatusesResponse resp = mono != null ? mono.subscribeOn(Schedulers.boundedElastic()).block() : null;
                if (resp != null && resp.customStatuses() != null) {
                    List<TicketFieldCustomStatusObject> list = Collections.unmodifiableList(new ArrayList<>(resp.customStatuses()));
                    customStatusesCache.set(new CacheEntry<>(list, now + CUSTOM_STATUS_CACHE_TTL_MS));
                    log.debug("Refreshed custom status cache with {} statuses (TTL: {}ms)", list.size(), CUSTOM_STATUS_CACHE_TTL_MS);
                    return list;
                } else {
                    List<TicketFieldCustomStatusObject> empty = Collections.emptyList();
                    customStatusesCache.set(new CacheEntry<>(empty, 0L));
                    return empty;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch custom statuses from Zendesk: {}", e.getMessage());
                if (current != null && current.data() != null && !current.data().isEmpty()) {
                    log.info("Using stale custom status cache due to fetch error");
                    return current.data();
                }
                throw new IllegalArgumentException("Failed to fetch custom statuses from Zendesk: " + e.getMessage(), e);
            }
        } finally {
            customStatusLock.unlock();
        }
    }

    public List<TicketFormStatus> getCachedTicketFormStatuses(boolean forceRefresh) {
        if (customStatusClient == null) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        CacheEntry<List<TicketFormStatus>> current = ticketFormStatusesCache.get();
        if (!forceRefresh && current != null && !current.isExpired(now)) {
            return current.data();
        }

        ticketFormStatusLock.lock();
        try {
            now = System.currentTimeMillis();
            current = ticketFormStatusesCache.get();
            if (!forceRefresh && current != null && !current.isExpired(now)) {
                return current.data();
            }

            try {
                Mono<TicketFormStatusesResponse> mono = customStatusClient.listTicketFormStatuses(null);
                TicketFormStatusesResponse resp = mono != null ? mono.subscribeOn(Schedulers.boundedElastic()).block() : null;
                if (resp != null && resp.ticketFormStatuses() != null) {
                    List<TicketFormStatus> list = Collections.unmodifiableList(new ArrayList<>(resp.ticketFormStatuses()));
                    ticketFormStatusesCache.set(new CacheEntry<>(list, now + CUSTOM_STATUS_CACHE_TTL_MS));
                    log.debug("Refreshed ticket form status cache with {} mappings (TTL: {}ms)", list.size(), CUSTOM_STATUS_CACHE_TTL_MS);
                    return list;
                } else {
                    List<TicketFormStatus> empty = Collections.emptyList();
                    ticketFormStatusesCache.set(new CacheEntry<>(empty, 0L));
                    return empty;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch ticket form statuses from Zendesk: {}", e.getMessage());
                if (current != null && current.data() != null && !current.data().isEmpty()) {
                    log.info("Using stale ticket form status cache due to fetch error");
                    return current.data();
                }
                List<TicketFormStatus> empty = Collections.emptyList();
                ticketFormStatusesCache.set(new CacheEntry<>(empty, 0L));
                return empty;
            }
        } finally {
            ticketFormStatusLock.unlock();
        }
    }

    public List<TicketForm> getCachedTicketForms(boolean forceRefresh) {
        if (ticketFormsClient == null) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        CacheEntry<List<TicketForm>> current = ticketFormsCache.get();
        if (!forceRefresh && current != null && !current.isExpired(now)) {
            return current.data();
        }

        ticketFormLock.lock();
        try {
            now = System.currentTimeMillis();
            current = ticketFormsCache.get();
            if (!forceRefresh && current != null && !current.isExpired(now)) {
                return current.data();
            }

            try {
                Mono<TicketFormsResponse> mono = ticketFormsClient.listTicketForms();
                TicketFormsResponse resp = mono != null ? mono.subscribeOn(Schedulers.boundedElastic()).block() : null;
                if (resp != null && resp.getTicketForms() != null) {
                    List<TicketForm> list = Collections.unmodifiableList(new ArrayList<>(resp.getTicketForms()));
                    ticketFormsCache.set(new CacheEntry<>(list, now + TICKET_FORM_CACHE_TTL_MS));
                    log.debug("Refreshed ticket form cache with {} forms (TTL: {}ms)", list.size(), TICKET_FORM_CACHE_TTL_MS);
                    return list;
                } else {
                    List<TicketForm> empty = Collections.emptyList();
                    ticketFormsCache.set(new CacheEntry<>(empty, 0L));
                    return empty;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch ticket forms from Zendesk: {}", e.getMessage());
                if (current != null && current.data() != null && !current.data().isEmpty()) {
                    log.info("Using stale ticket form cache due to fetch error");
                    return current.data();
                }
                List<TicketForm> empty = Collections.emptyList();
                ticketFormsCache.set(new CacheEntry<>(empty, 0L));
                return empty;
            }
        } finally {
            ticketFormLock.unlock();
        }
    }

    public TicketForm validateTicketForm(Long ticketFormId) {
        if (ticketFormId == null) return null;
        if (ticketFormsClient == null) {
            log.warn("TicketFormsClient not configured; skipping remote ticket form validation for ID: {}", ticketFormId);
            return null;
        }

        List<TicketForm> forms = getCachedTicketForms(false);
        if (forms == null || forms.isEmpty()) {
            return null;
        }

        Optional<TicketForm> matchOpt = forms.stream()
                .filter(f -> f != null && f.getId() != null && f.getId().equals(ticketFormId))
                .findFirst();

        if (matchOpt.isEmpty()) {
            String activeList = forms.stream()
                    .filter(f -> f != null && Boolean.TRUE.equals(f.getActive()))
                    .map(f -> String.format("[%d: '%s']", f.getId(), f.getName()))
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Ticket form ID " + ticketFormId + " does not exist. Active ticket forms are: " + (activeList.isEmpty() ? "none" : activeList));
        }

        TicketForm form = matchOpt.get();
        if (!Boolean.TRUE.equals(form.getActive())) {
            throw new IllegalArgumentException("Ticket form ID " + ticketFormId + " ('" + form.getName() + "') is inactive.");
        }

        return form;
    }

    public TicketFieldCustomStatusObject validateCustomStatus(Long customStatusId, @Nullable String targetStatus) {
        if (customStatusId == null) return null;
        if (customStatusClient == null) {
            log.warn("CustomStatusClient not configured; skipping remote custom status validation for ID: {}", customStatusId);
            return null;
        }

        List<TicketFieldCustomStatusObject> statuses = getCachedCustomStatuses(false);
        if (statuses == null || statuses.isEmpty()) {
            throw new IllegalArgumentException("No custom statuses found for this Zendesk account. Make sure custom ticket statuses are enabled.");
        }

        Optional<TicketFieldCustomStatusObject> matchOpt = statuses.stream()
                .filter(s -> s != null && s.getId() != null && s.getId().equals(customStatusId))
                .findFirst();

        if (matchOpt.isEmpty()) {
            String activeList = statuses.stream()
                    .filter(s -> s != null && Boolean.TRUE.equals(s.getActive()))
                    .map(s -> String.format("[%d: '%s' (%s)]", s.getId(), s.getAgentLabel(), s.getStatusCategory() != null ? s.getStatusCategory().getValue() : "unknown"))
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Custom status ID " + customStatusId + " does not exist. Active custom statuses are: " + (activeList.isEmpty() ? "none" : activeList));
        }

        TicketFieldCustomStatusObject statusObj = matchOpt.get();
        if (!Boolean.TRUE.equals(statusObj.getActive())) {
            throw new IllegalArgumentException("Custom status ID " + customStatusId + " ('" + statusObj.getAgentLabel() + "') is inactive.");
        }

        if (StringUtils.isNotEmpty(targetStatus)) {
            String expectedCat = targetStatus.trim().toLowerCase();
            String actualCat = statusObj.getStatusCategory() != null ? statusObj.getStatusCategory().getValue() : null;
            if (actualCat != null && !actualCat.equalsIgnoreCase(expectedCat)) {
                String matchingStatuses = statuses.stream()
                        .filter(s -> s != null && Boolean.TRUE.equals(s.getActive()) && s.getStatusCategory() != null && expectedCat.equalsIgnoreCase(s.getStatusCategory().getValue()))
                        .map(s -> String.format("[%d: '%s']", s.getId(), s.getAgentLabel()))
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(String.format(
                        "Custom status ID %d ('%s') belongs to category '%s', which does not match status '%s'. Valid active custom statuses for '%s' are: %s",
                        customStatusId, statusObj.getAgentLabel(), actualCat, expectedCat, expectedCat,
                        matchingStatuses.isEmpty() ? "none" : matchingStatuses
                ));
            }
        }

        return statusObj;
    }

    public void validateTicketCustomStatusCategoryMatch(Long ticketId, Ticket currentTicket, TicketFieldCustomStatusObject customStatus, Long customStatusId) {
        if (customStatus == null || currentTicket == null || currentTicket.getStatus() == null) return;
        String customCat = customStatus.getStatusCategory() != null ? customStatus.getStatusCategory().getValue() : null;
        String currentStatusVal = currentTicket.getStatus().getValue();
        if (customCat != null && !customCat.equalsIgnoreCase(currentStatusVal)) {
            throw new IllegalArgumentException(String.format(
                    "Custom status ID %d ('%s') belongs to category '%s', but ticket #%d currently has status '%s'. To change to a custom status in a different category, you must also provide the matching 'status' parameter (status='%s').",
                    customStatusId, customStatus.getAgentLabel(), customCat, ticketId, currentStatusVal, customCat
            ));
        }
    }

    public void validateCustomStatusForForm(TicketFieldCustomStatusObject customStatus, Long ticketFormId) {
        if (customStatus == null || ticketFormId == null) return;
        if (Boolean.TRUE.equals(customStatus.getIsDefault())) {
            return;
        }
        List<TicketFormStatus> formStatuses = getCachedTicketFormStatuses(false);
        if (formStatuses == null || formStatuses.isEmpty()) {
            return;
        }

        boolean formHasSpecificStatuses = formStatuses.stream()
                .anyMatch(fs -> fs != null && ticketFormId.equals(fs.ticketFormId()));
        if (!formHasSpecificStatuses) {
            return;
        }

        boolean statusAllowedOnForm = formStatuses.stream()
                .anyMatch(fs -> fs != null && ticketFormId.equals(fs.ticketFormId()) && customStatus.getId().equals(fs.customStatusId()));

        if (!statusAllowedOnForm) {
            List<TicketFieldCustomStatusObject> allStatuses = getCachedCustomStatuses(false);
            String category = customStatus.getStatusCategory() != null ? customStatus.getStatusCategory().getValue() : null;
            String validForForm = allStatuses.stream()
                    .filter(s -> s != null && Boolean.TRUE.equals(s.getActive()))
                    .filter(s -> category == null || (s.getStatusCategory() != null && category.equalsIgnoreCase(s.getStatusCategory().getValue())))
                    .filter(s -> Boolean.TRUE.equals(s.getIsDefault()) || formStatuses.stream().anyMatch(fs -> ticketFormId.equals(fs.ticketFormId()) && s.getId().equals(fs.customStatusId())))
                    .map(s -> String.format("[%d: '%s']", s.getId(), s.getAgentLabel()))
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException(String.format(
                    "Custom status ID %d ('%s') cannot be used with ticket form #%d. Valid active custom statuses for this form%s are: %s.",
                    customStatus.getId(),
                    customStatus.getAgentLabel(),
                    ticketFormId,
                    category != null ? " under category '" + category + "'" : "",
                    validForForm.isEmpty() ? "none" : validForForm
            ));
        }
    }
}
