package gov.medicaid.pem.kit.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * The event envelope of every PEM event (ADR-003, java-microservices 7): {@code subject} is the NPI
 * and the Kafka key; {@code time} is always ISO-8601, whatever the ObjectMapper settings.
 */
public record EventEnvelope(
    UUID id,
    String type,
    String source,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant time,
    String subject,
    String correlationId,
    JsonNode data) {}
