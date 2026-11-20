package gov.medicaid.pem.kit.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

  private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

  @Test
  @DisplayName(
      "[MAM-31 AC-1] the envelope has id, type, source, time, subject, correlationId and data (ADR-003)")
  void envelopeRoundTripsThroughJson() throws Exception {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    EventEnvelope e =
        new EventEnvelope(
            id,
            "ProviderRegistered",
            "provider-service",
            Instant.parse("2026-01-15T10:00:00Z"),
            "1003000111",
            "corr-1",
            json.valueToTree(Map.of("npi", "1003000111", "enrollStatus", "P")));

    String text = json.writeValueAsString(e);
    EventEnvelope back = json.readValue(text, EventEnvelope.class);

    assertThat(back).isEqualTo(e);
    assertThat(json.readTree(text).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "type", "source", "time", "subject", "correlationId", "data");
    assertThat(json.readTree(text).get("time").asText()).isEqualTo("2026-01-15T10:00:00Z");
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-1] each producer has one topic pem.<service>.events (java-microservices 7)")
  void topicPerProducer() {
    assertThat(Topics.forService("provider-service")).isEqualTo("pem.provider.events");
    assertThat(Topics.forService("risk-service")).isEqualTo("pem.risk.events");
    assertThat(Topics.forService("tmsis-service")).isEqualTo("pem.tmsis.events");
  }
}
