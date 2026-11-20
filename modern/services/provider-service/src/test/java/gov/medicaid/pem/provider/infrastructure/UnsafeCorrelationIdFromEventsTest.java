package gov.medicaid.pem.provider.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.LogCapture;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.provider.TestEvents;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A correlation id taken from an incoming event never reaches the log, the history or an outgoing
 * event unsanitised (lesson of the earlier reviews, MAM-36 AC-4): an id with a line break or a
 * forged log line is replaced by a generated uuid, and the same safe id is in the log lines of the
 * handling, in the history entry and in the event the handling causes. The events come through the
 * real Kafka, as the other services send them.
 */
class UnsafeCorrelationIdFromEventsTest extends IntegrationTest {

  static final String NPI = "1003000666";
  static final String FORGED = "abc\nERROR forged line from the sender";
  static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    ProviderRows.wipe(jdbc);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", NPI);
  }

  /** No captured line holds the forged text or an unsafe id, and one line of the class is there. */
  private static void assertSafeLogOf(LogCapture log, String loggerName, Level level) {
    List<LogCapture.Line> lines = log.lines();
    assertThat(lines)
        .allSatisfy(
            l -> {
              assertThat(l.message()).doesNotContain("forged");
              if (l.correlationId() != null) {
                assertThat(l.correlationId()).matches(SAFE);
              }
            });
    assertThat(lines)
        .as("a %s line of %s written while the event was handled", level, loggerName)
        .anyMatch(
            l ->
                l.logger().endsWith(loggerName)
                    && l.level() == level
                    && l.correlationId() != null
                    && SAFE.matcher(l.correlationId()).matches());
  }

  private String historyCorrelationId(String action) {
    List<String> ids =
        jdbc.queryForList(
            "SELECT correlation_id::text FROM audit_log WHERE npi = ? AND action = ?",
            String.class,
            NPI,
            action);
    assertThat(ids).as("%s entries of %s", action, NPI).hasSize(1);
    return ids.get(0);
  }

  @Test
  @DisplayName(
      "[MAM-17 AC-2] a registration caused by an event with a forged correlation id logs, records and announces one generated uuid, never the forged text")
  void forgedIdOnApplicationValidated() throws Exception {
    try (LogCapture log = LogCapture.start()) {
      TestEvents.sendAndAwait(
          json,
          jdbc,
          TestEvents.ENROLLMENT,
          UUID.randomUUID(),
          "ApplicationValidated",
          "enrollment-service",
          NPI,
          FORGED,
          TestEvents.individualApplication(NPI, "MCD000666", "RIVERA", "MARIA"));

      assertSafeLogOf(log, "RegisterProvider", Level.INFO);
    }

    List<String> events =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ProviderRegistered' AND subject = ?",
            String.class,
            NPI);
    assertThat(events).hasSize(1);
    JsonNode registered = json.readTree(events.get(0));
    String announced = registered.path("correlationId").asText();
    assertThat(announced).doesNotContain("forged").doesNotContain("\n").hasSize(36);
    assertThat(UUID.fromString(announced)).isNotNull();
    assertThat(historyCorrelationId("REGISTERED")).isEqualTo(announced);
  }

  @Test
  @DisplayName(
      "[MAM-21 AC-1] a screening verdict from an event with a forged correlation id is recorded in the history under one generated uuid, never the forged text")
  void forgedIdOnScreeningCompleted() {
    ProviderRows.individual(NPI).status("P").flag("U").insert(jdbc);

    TestEvents.sendAndAwait(
        json,
        jdbc,
        TestEvents.SCREENING,
        UUID.randomUUID(),
        "ScreeningCompleted",
        "screening-service",
        NPI,
        FORGED,
        TestEvents.screening(NPI, "PASS"));

    String recorded = historyCorrelationId("SCREENING_VERDICT");
    assertThat(recorded).doesNotContain("forged").hasSize(36);
    assertThat(UUID.fromString(recorded)).isNotNull();
  }

  @Test
  @DisplayName(
      "[MAM-21 AC-1] a screening verdict for an NPI that is not on file, from an event with a forged correlation id, is logged as a warning under one generated uuid, never the forged text")
  void forgedIdOnScreeningCompletedForUnknownProvider() {
    try (LogCapture log = LogCapture.start()) {
      TestEvents.sendAndAwait(
          json,
          jdbc,
          TestEvents.SCREENING,
          UUID.randomUUID(),
          "ScreeningCompleted",
          "screening-service",
          NPI,
          FORGED,
          TestEvents.screening(NPI, "PASS"));

      assertSafeLogOf(log, "RecordVerdicts", Level.WARN);
    }
  }

  @Test
  @DisplayName(
      "[MAM-23 AC-1] a risk tier from an event with a forged correlation id is recorded in the history under one generated uuid, never the forged text")
  void forgedIdOnRiskTierAssigned() {
    ProviderRows.individual(NPI).status("P").flag("P").insert(jdbc);

    TestEvents.sendAndAwait(
        json,
        jdbc,
        TestEvents.RISK,
        UUID.randomUUID(),
        "RiskTierAssigned",
        "risk-service",
        NPI,
        FORGED,
        TestEvents.riskTier(NPI, "H"));

    String recorded = historyCorrelationId("RISK_TIER_ASSIGNED");
    assertThat(recorded).doesNotContain("forged").hasSize(36);
    assertThat(UUID.fromString(recorded)).isNotNull();
  }

  @Test
  @DisplayName(
      "[MAM-23 AC-1] a risk tier for an NPI that is not on file, from an event with a forged correlation id, is logged as a warning under one generated uuid, never the forged text")
  void forgedIdOnRiskTierAssignedForUnknownProvider() {
    try (LogCapture log = LogCapture.start()) {
      TestEvents.sendAndAwait(
          json,
          jdbc,
          TestEvents.RISK,
          UUID.randomUUID(),
          "RiskTierAssigned",
          "risk-service",
          NPI,
          FORGED,
          TestEvents.riskTier(NPI, "H"));

      assertSafeLogOf(log, "RecordVerdicts", Level.WARN);
    }
  }
}
