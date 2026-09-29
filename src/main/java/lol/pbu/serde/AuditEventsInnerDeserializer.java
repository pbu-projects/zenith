package lol.pbu.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lol.pbu.z4j.model.AuditEventsInner;
import lol.pbu.z4j.model.Via;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

@Singleton
public class AuditEventsInnerDeserializer implements Deserializer<AuditEventsInner> {

    private static final Logger log = LoggerFactory.getLogger(AuditEventsInnerDeserializer.class);

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "id", "type", "field_name", "body", "value", "previous_value", "via", "subject", "recipients"
    );

    private final Provider<ObjectMapper> objectMapperProvider;

    @Inject
    public AuditEventsInnerDeserializer(Provider<ObjectMapper> objectMapperProvider) {
        this.objectMapperProvider = objectMapperProvider;
    }

    @Override
    public AuditEventsInner deserialize(
            @NonNull Decoder decoder,
            @NonNull DecoderContext context,
            @NonNull Argument<? super AuditEventsInner> type
    ) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (node.isNull() || !node.isObject()) {
            return null;
        }

        AuditEventsInner event = new AuditEventsInner();

        populateId(node, event);
        populateStringFields(node, event);
        populateBody(node, event);
        preserveUnmappedFieldsIfBodyEmpty(node, event);
        populateValues(node, event);
        populateVia(node, event);
        populateRecipients(node, event);

        return event;
    }

    private void populateId(JsonNode node, AuditEventsInner event) {
        JsonNode idNode = node.get("id");
        if (idNode != null) {
            if (idNode.isNumber()) {
                event.setId(idNode.getLongValue());
            } else if (idNode.isString()) {
                try {
                    event.setId(Long.parseLong(idNode.getStringValue().trim()));
                } catch (NumberFormatException _) {
                    // Ignore non-numeric id
                }
            }
        }
    }

    private void populateStringFields(JsonNode node, AuditEventsInner event) {
        JsonNode typeNode = node.get("type");
        if (typeNode != null && !typeNode.isNull()) {
            event.setType(typeNode.isString() ? typeNode.getStringValue() : typeNode.coerceStringValue());
        }

        JsonNode fieldNameNode = node.get("field_name");
        if (fieldNameNode != null && !fieldNameNode.isNull()) {
            event.setFieldName(fieldNameNode.isString() ? fieldNameNode.getStringValue() : fieldNameNode.coerceStringValue());
        }

        JsonNode subjectNode = node.get("subject");
        if (subjectNode != null && !subjectNode.isNull()) {
            event.setSubject(subjectNode.isString() ? subjectNode.getStringValue() : subjectNode.coerceStringValue());
        }
    }

    private void populateBody(JsonNode node, AuditEventsInner event) throws IOException {
        JsonNode bodyNode = node.get("body");
        if (bodyNode != null && !bodyNode.isNull()) {
            if (bodyNode.isString()) {
                event.setBody(bodyNode.getStringValue());
            } else {
                ObjectMapper mapper = objectMapperProvider.get();
                if (mapper != null) {
                    event.setBody(mapper.writeValueAsString(bodyNode));
                } else {
                    event.setBody(bodyNode.coerceStringValue());
                }
            }
        }
    }

    private void preserveUnmappedFieldsIfBodyEmpty(JsonNode node, AuditEventsInner event) throws IOException {
        if (event.getBody() != null && !event.getBody().isBlank()) {
            return;
        }
        Map<String, Object> unmapped = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            if (!KNOWN_FIELDS.contains(entry.getKey())) {
                unmapped.put(entry.getKey(), extractArbitraryValue(entry.getValue()));
            }
        }
        if (!unmapped.isEmpty()) {
            ObjectMapper mapper = objectMapperProvider.get();
            if (mapper != null) {
                event.setBody(mapper.writeValueAsString(unmapped));
            }
        }
    }

    private void populateValues(JsonNode node, AuditEventsInner event) {
        JsonNode valueNode = node.get("value");
        if (valueNode != null && !valueNode.isNull()) {
            event.setValue(extractArbitraryValue(valueNode));
        }

        JsonNode prevValueNode = node.get("previous_value");
        if (prevValueNode != null && !prevValueNode.isNull()) {
            event.setPreviousValue(extractArbitraryValue(prevValueNode));
        }
    }

    private void populateVia(JsonNode node, AuditEventsInner event) {
        JsonNode viaNode = node.get("via");
        if (viaNode != null && !viaNode.isNull()) {
            try {
                ObjectMapper mapper = objectMapperProvider.get();
                if (mapper != null) {
                    event.setVia(mapper.readValueFromTree(viaNode, Via.class));
                }
            } catch (Exception e) {
                log.debug("Failed to deserialize audit event 'via' structure, ignoring to preserve audit stream: {}", e.getMessage());
            }
        }
    }

    private void populateRecipients(JsonNode node, AuditEventsInner event) {
        JsonNode recipientsNode = node.get("recipients");
        if (recipientsNode != null && recipientsNode.isArray()) {
            List<Long> recipients = new ArrayList<>();
            for (JsonNode r : recipientsNode.values()) {
                if (r.isNumber()) {
                    recipients.add(r.getLongValue());
                } else if (r.isString()) {
                    try {
                        recipients.add(Long.parseLong(r.getStringValue().trim()));
                    } catch (NumberFormatException e) {
                        log.debug("Skipping non-numeric recipient ID '{}': {}", r.getStringValue(), e.getMessage());
                    }
                }
            }
            event.setRecipients(recipients);
        }
    }

    @Nullable
    private Object extractArbitraryValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isString()) {
            return node.getStringValue();
        }
        if (node.isBoolean()) {
            return node.getBooleanValue();
        }
        if (node.isNumber()) {
            return node.getNumberValue();
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.values().spliterator(), false)
                    .map(this::extractArbitraryValue)
                    .toList();
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                map.put(entry.getKey(), extractArbitraryValue(entry.getValue()));
            }
            return map;
        }
        return node.coerceStringValue();
    }
}
