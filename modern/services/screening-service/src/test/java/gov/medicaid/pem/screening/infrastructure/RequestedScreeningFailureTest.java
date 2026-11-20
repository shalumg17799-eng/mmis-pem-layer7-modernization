package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.LogCapture;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-28 AC-1 and lessons a and c (decision D-024): a requested screening runs in the background
 * after the 202 answer. When the database rejects one of its writes nothing of it is kept (the
 * screening, its results and its ScreeningCompleted are one transaction), the failure is not
 * silent: the full exception is logged at ERROR with the message of the database itself, and the
 * next request for the provider works.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class RequestedScreeningFailureTest extends Lane3Test {

  static final String NPI = "1003000555";
  static final String PATH = "/api/v1/screenings";

  void provider() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "ECHO", "MD55555", "TX"));
    ScreeningWorld.licence("MD55555", "TX", "ACTIVE", "2029-01", 3);
  }

  /** Requests the screening as ANALYST (202) and waits until the database has refused its write. */
  LogCapture requestedWhileDatabaseRejects(String trigger, String table, String when)
      throws Exception {
    provider();
    reject(trigger, "INSERT", table, when);
    LogCapture log = LogCapture.open();
    try {
      MvcResult r = post(PATH, PemJwt.analyst(), "{\"npi\":\"" + NPI + "\"}");
      assertThat(r.getResponse().getStatus())
          .as(r.getResponse().getContentAsString())
          .isEqualTo(202);
      until(
          "the failure of the requested screening is logged at ERROR",
          () ->
              log.lines().stream()
                  .anyMatch(
                      l ->
                          l.level() == Level.ERROR
                              && l.everything().contains("the test database rejected this write")),
          BOUND);
      return log;
    } catch (Throwable t) {
      log.close();
      throw t;
    }
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] a requested screening whose insert is rejected by the database stores nothing and announces nothing (lesson c)")
  void screeningInsertRejected() throws Exception {
    try (LogCapture ignored =
        requestedWhileDatabaseRejects("reject_manual", "screening", "NEW.npi = '" + NPI + "'")) {
      assertThat(screeningCount(NPI)).isZero();
      assertThat(completedEvents(NPI)).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM screening_result r JOIN screening s ON s.id = r.screening_id"
                      + " WHERE s.npi = ?",
                  Integer.class,
                  NPI))
          .isZero();
    }
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] a requested screening whose ScreeningCompleted is rejected by the database is not kept without its event (lesson c)")
  void outboxInsertRejected() throws Exception {
    try (LogCapture ignored =
        requestedWhileDatabaseRejects(
            "reject_manual_outbox", "outbox_event", "NEW.subject = '" + NPI + "'")) {
      assertThat(screeningCount(NPI))
          .as("a screening kept although its ScreeningCompleted could not be written")
          .isZero();
      assertThat(completedEvents(NPI)).isZero();
    }
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] the failure of a requested screening is logged at ERROR with the exception itself (D-024, lesson a)")
  void fullExceptionIsLogged() throws Exception {
    try (LogCapture log =
        requestedWhileDatabaseRejects("reject_manual", "screening", "NEW.npi = '" + NPI + "'")) {
      assertThat(log.lines())
          .filteredOn(l -> l.level() == Level.ERROR && l.hasThrowable())
          .as("an ERROR event with the exception itself, not only its text")
          .anySatisfy(
              l -> assertThat(l.everything()).contains("the test database rejected this write"));
    }
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] after a database error the next request for the provider is screened once and announced once")
  void nextRequestWorks() throws Exception {
    try (LogCapture ignored =
        requestedWhileDatabaseRejects("reject_manual", "screening", "NEW.npi = '" + NPI + "'")) {
      lift("reject_manual");
    }
    screen(NPI);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
  }
}
