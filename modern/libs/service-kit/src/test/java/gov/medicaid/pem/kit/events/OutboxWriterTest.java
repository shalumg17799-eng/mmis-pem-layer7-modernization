package gov.medicaid.pem.kit.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxWriterTest {

  private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
  private final OutboxWriter writer = new OutboxWriter(jdbc, json, clock, "provider-service");

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-2] an event is written to outbox_event in the caller's transaction with the full envelope")
  void writesTheEnvelopeToTheOutboxTable() throws Exception {
    MDC.put(CorrelationId.MDC_KEY, "5a1e7b20-3c4d-4e5f-9a6b-000000000456");

    UUID id = writer.enqueue("ProviderRegistered", "1003000111", Map.of("npi", "1003000111"));

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .update(
            eq(OutboxWriter.INSERT_SQL),
            eq(id),
            eq("ProviderRegistered"),
            eq("1003000111"),
            payload.capture(),
            eq(java.sql.Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))));
    JsonNode env = json.readTree(payload.getValue());
    assertThat(env.get("id").asText()).isEqualTo(id.toString());
    assertThat(env.get("type").asText()).isEqualTo("ProviderRegistered");
    assertThat(env.get("source").asText()).isEqualTo("provider-service");
    assertThat(env.get("time").asText()).isEqualTo("2026-01-15T10:00:00Z");
    assertThat(env.get("subject").asText()).isEqualTo("1003000111");
    assertThat(env.get("correlationId").asText()).isEqualTo("5a1e7b20-3c4d-4e5f-9a6b-000000000456");
    assertThat(env.get("data").get("npi").asText()).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("[MAM-31 AC-5] without a current correlation id the event gets a new one")
  void generatesACorrelationIdWhenNoneIsActive() throws Exception {
    writer.enqueue("ProviderRegistered", "1003000111", Map.of());

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .update(
            eq(OutboxWriter.INSERT_SQL),
            org.mockito.ArgumentMatchers.any(UUID.class),
            anyString(),
            anyString(),
            payload.capture(),
            org.mockito.ArgumentMatchers.any());
    assertThat(json.readTree(payload.getValue()).get("correlationId").asText()).isNotBlank();
  }
}
