package gov.medicaid.pem.kit.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProcessedEventsTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ProcessedEvents processed = new ProcessedEvents(jdbc);
  private final UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

  @Test
  @DisplayName("[MAM-31 AC-4] the first delivery of an event id is processed")
  void firstDeliveryIsNew() {
    when(jdbc.update(ProcessedEvents.INSERT_SQL, id)).thenReturn(1);
    assertThat(processed.firstTime(id)).isTrue();
  }

  @Test
  @DisplayName("[MAM-31 AC-4] a redelivered event id is skipped (idempotent consumer)")
  void redeliveryIsSkipped() {
    when(jdbc.update(ProcessedEvents.INSERT_SQL, id)).thenReturn(0);
    assertThat(processed.firstTime(id)).isFalse();
  }

  @Test
  @DisplayName("[MAM-31 AC-1] an incoming message is parsed into the envelope and its data")
  void parsesIncomingMessages() {
    var json = JsonMapper.builder().findAndAddModules().build();
    String msg =
        "{\"id\":\"00000000-0000-0000-0000-0000000000bb\",\"type\":\"ApplicationValidated\","
            + "\"source\":\"enrollment-service\",\"time\":\"2026-01-15T10:00:00Z\","
            + "\"subject\":\"1003000111\",\"correlationId\":\"c1\",\"data\":{\"npi\":\"1003000111\"}}";

    EventEnvelope e = EventJson.parse(json, msg);

    assertThat(e.id()).isEqualTo(id);
    assertThat(e.type()).isEqualTo("ApplicationValidated");
    assertThat(EventJson.data(json, e, Sample.class).npi()).isEqualTo("1003000111");
  }

  record Sample(String npi) {}
}
