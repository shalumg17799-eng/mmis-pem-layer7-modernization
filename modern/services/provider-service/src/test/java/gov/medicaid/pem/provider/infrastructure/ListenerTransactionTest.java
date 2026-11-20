package gov.medicaid.pem.provider.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.provider.TestEvents;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The processed-event marker is written in the same transaction as the business write (lesson of
 * the earlier reviews, ADR-003): when the business write fails, the marker is rolled back with it,
 * so that the re-run applies the event once, and never twice and never not at all. The failure is
 * injected in the database with a trigger that is removed again in every case.
 */
class ListenerTransactionTest extends IntegrationTest {

  static final String NPI = "1003000333";

  @Autowired EnrollmentEventsListener enrollment;
  @Autowired VerdictEventsListener verdicts;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    ProviderRows.wipe(jdbc);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", NPI);
  }

  private int processed(UUID id) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id);
    return n == null ? 0 : n;
  }

  private void failProviderWrites(String operation) {
    jdbc.execute(
        "CREATE FUNCTION fail_provider_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
            + " RAISE EXCEPTION 'injected failure after the marker'; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_provider_write BEFORE "
            + operation
            + " ON provider FOR EACH ROW EXECUTE FUNCTION fail_provider_write()");
  }

  private void allowProviderWrites() {
    jdbc.execute("DROP TRIGGER IF EXISTS fail_provider_write ON provider");
    jdbc.execute("DROP FUNCTION IF EXISTS fail_provider_write()");
  }

  @Test
  @DisplayName(
      "[MAM-17 AC-6] when registering fails the processed marker is rolled back too, and the re-run registers the provider once and announces it once")
  void registrationMarkerRollsBack() {
    UUID event = UUID.randomUUID();
    String message =
        TestEvents.envelope(
            json,
            event,
            "ApplicationValidated",
            "enrollment-service",
            NPI,
            UUID.randomUUID().toString(),
            TestEvents.individualApplication(NPI, "MCD000333", "RIVERA", "MARIA"));
    failProviderWrites("INSERT");
    try {
      assertThatThrownBy(() -> enrollment.onEvent(message)).isInstanceOf(RuntimeException.class);

      assertThat(processed(event)).as("marker after the failed run").isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM provider WHERE npi = ?", Integer.class, NPI))
          .isZero();
    } finally {
      allowProviderWrites();
    }

    enrollment.onEvent(message);
    enrollment.onEvent(message);

    assertThat(processed(event)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM provider WHERE npi = ?", Integer.class, NPI))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ProviderRegistered' AND subject = ?",
                Integer.class,
                NPI))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-21 AC-6] when saving the verdict fails the processed marker is rolled back too, and the re-run applies the verdict once")
  void verdictMarkerRollsBack() {
    ProviderRows.individual(NPI).status("P").flag("U").insert(jdbc);
    UUID event = UUID.randomUUID();
    String message =
        TestEvents.envelope(
            json,
            event,
            "ScreeningCompleted",
            "screening-service",
            NPI,
            UUID.randomUUID().toString(),
            TestEvents.screening(NPI, "PASS"));
    failProviderWrites("UPDATE");
    try {
      assertThatThrownBy(() -> verdicts.onEvent(message)).isInstanceOf(RuntimeException.class);

      assertThat(processed(event)).as("marker after the failed run").isZero();
      assertThat(flag()).isEqualTo("U");
    } finally {
      allowProviderWrites();
    }

    verdicts.onEvent(message);
    verdicts.onEvent(message);

    assertThat(processed(event)).isEqualTo(1);
    assertThat(flag()).isEqualTo("P");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE npi = ? AND action = 'SCREENING_VERDICT'",
                Integer.class,
                NPI))
        .as("one history entry for the one application of the verdict")
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-23 AC-6] when saving the tier fails the processed marker is rolled back too, and the re-run applies the tier once")
  void tierMarkerRollsBack() {
    ProviderRows.individual(NPI).status("P").flag("P").insert(jdbc);
    UUID event = UUID.randomUUID();
    String message =
        TestEvents.envelope(
            json,
            event,
            "RiskTierAssigned",
            "risk-service",
            NPI,
            UUID.randomUUID().toString(),
            TestEvents.riskTier(NPI, "H"));
    failProviderWrites("UPDATE");
    try {
      assertThatThrownBy(() -> verdicts.onEvent(message)).isInstanceOf(RuntimeException.class);

      assertThat(processed(event)).as("marker after the failed run").isZero();
      assertThat(tier()).isNull();
    } finally {
      allowProviderWrites();
    }

    verdicts.onEvent(message);
    verdicts.onEvent(message);

    assertThat(processed(event)).isEqualTo(1);
    assertThat(tier()).isEqualTo("H");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE npi = ? AND action = 'RISK_TIER_ASSIGNED'",
                Integer.class,
                NPI))
        .isEqualTo(1);
  }

  private String flag() {
    Map<String, Object> row =
        jdbc.queryForMap("SELECT screen_flag FROM provider WHERE npi = ?", NPI);
    return ((String) row.get("screen_flag")).trim();
  }

  private String tier() {
    Map<String, Object> row = jdbc.queryForMap("SELECT risk_tier FROM provider WHERE npi = ?", NPI);
    Object t = row.get("risk_tier");
    return t == null ? null : ((String) t).trim();
  }
}
