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
package lol.pbu.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;
import lol.pbu.z4j.model.TicketCreateRequest;

import java.io.IOException;

/**
 * Custom serializer for {@link TicketCreateRequest} that dynamically resolves and dispatches
 * to the runtime class serializer of the enclosed ticket object.
 * This guarantees polymorphic fields (e.g. additional_tags, remove_tags) are preserved on the wire.
 */
@Singleton
public class TicketCreateRequestSerializer implements Serializer<TicketCreateRequest> {

    @Override
    public void serialize(
            @NonNull Encoder encoder,
            @NonNull EncoderContext context,
            @NonNull Argument<? extends TicketCreateRequest> type,
            @NonNull TicketCreateRequest value
    ) throws IOException {
        Encoder objectEncoder = encoder.encodeObject(type);
        if (value.getTicket() != null) {
            objectEncoder.encodeKey(TicketCreateRequest.JSON_PROPERTY_TICKET);
            serializeTicket(objectEncoder, context, value.getTicket());
        }
        objectEncoder.finishStructure();
    }

    @SuppressWarnings("unchecked")
    private <T> void serializeTicket(Encoder encoder, EncoderContext context, T ticket) throws IOException {
        Class<T> clazz = (Class<T>) ticket.getClass();
        Argument<T> arg = Argument.of(clazz);
        Serializer<? super T> serializer = context.findSerializer(arg).createSpecific(context, arg);
        ((Serializer<T>) serializer).serialize(encoder, context, arg, ticket);
    }
}
