package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Which date a completed revalidation leaves behind (MAM-48 AC-7, D-003, question C-10), and which
 * date the tests of this service assume (question Q-018).
 *
 * <p>revalidation-service never computes a due date. The five-year date after a PASS is set by
 * provider-service and reaches this service only as the revalidationDueDate of a
 * ProviderStatusChanged (A to A). The parity comparison found that provider-service writes the
 * processing date of its own clock plus five years, not the business date of the sweep plus five
 * years (Q-018, default A applied, nothing to change). So these tests give the next date as event
 * data and use both candidates, 2031-03-31 (the business date of the sweep, 2026-03-31, plus five
 * years: the example of MAM-48 AC-7) and 2031-11-21 (the processing date 2026-11-21 plus five
 * years): the sweeps must follow whichever date the event carries, on the day it arrives and not a
 * day before, and nothing in this service may derive a date from the clock or from the business
 * date.
 */
@Timeout(240)
class NextDueDateFollowsProviderTest extends IntegrationTest {

  static final String NPI = "1003000222";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProviderEventsListener providerEvents;
  @Autowired ScreeningEventsListener screeningEvents;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private String trackedDueDate() {
    return jdbc.queryForObject(
        "SELECT reval_due_date::text FROM tracked_provider WHERE npi = ?", String.class, NPI);
  }

  private int openCases() {
    return RevalidationFixtures.count(
        jdbc,
        "SELECT count(*) FROM revalidation_case WHERE npi = ? AND status IN ('DUE', 'IN_SCREENING')",
        NPI);
  }

  private List<String> openCaseDueDates() {
    return jdbc.queryForList(
        "SELECT due_date::text FROM revalidation_case WHERE npi = ? AND status IN ('DUE', 'IN_SCREENING')",
        String.class,
        NPI);
  }

  /** The provider is ACTIVE, due 2026-02-28, and the sweep of 2026-03-31 opened its case. */
  private void providerWithOneCase() throws Exception {
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(UUID.randomUUID(), NPI, "P", "A", "2026-02-28"));
    RevalidationFixtures.sweep(mvc, "2026-03-31");
    assertThat(RevalidationFixtures.casesOf(jdbc, NPI)).isEqualTo(1);
    assertThat(openCases()).isEqualTo(1);
  }

  private void passingRescreening() {
    screeningEvents.onEvent(
        RevalidationFixtures.screeningCompleted(
            UUID.randomUUID(), NPI, "REVALIDATION", "PASS", UUID.randomUUID()));
  }

  @ParameterizedTest(
      name =
          "[MAM-48 AC-7] after a PASS the sweeps follow the next due date that provider-service publishes: {0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "the business date of the sweep plus five years | 2031-03-31 | 2031-03-30",
        "the processing date of the provider-service clock plus five years (Q-018) | 2031-11-21 | 2031-11-20"
      })
  void followsTheDateOfTheEvent(String basis, LocalDate next, LocalDate dayBefore)
      throws Exception {
    providerWithOneCase();

    passingRescreening();
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(
            UUID.randomUUID(), NPI, "A", "A", next.toString()));

    assertThat(trackedDueDate()).as("the date of the event is tracked").isEqualTo(next.toString());
    RevalidationFixtures.sweep(mvc, dayBefore.toString());
    assertThat(RevalidationFixtures.casesOf(jdbc, NPI))
        .as("the day before %s nothing is due: %s", next, basis)
        .isEqualTo(1);
    RevalidationFixtures.sweep(mvc, next.toString());
    assertThat(RevalidationFixtures.casesOf(jdbc, NPI)).as("a new case on %s", next).isEqualTo(2);
    assertThat(openCaseDueDates()).containsExactly(next.toString());
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-7] completing the case with a PASS computes and announces no date here: the tracked date stays until provider-service publishes one")
  void aPassComputesNoDate() throws Exception {
    providerWithOneCase();

    passingRescreening();

    assertThat(openCases()).as("the case is closed").isZero();
    assertThat(trackedDueDate()).isEqualTo("2026-02-28");
    assertThat(
            RevalidationFixtures.count(
                jdbc, "SELECT count(*) FROM outbox_event WHERE type <> 'RevalidationDue'"))
        .as("no event other than the RevalidationDue of the case that was opened")
        .isZero();
    assertThat(RevalidationFixtures.outbox(jdbc, "RevalidationDue")).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-5] a FAIL moves no date either: the case is closed, the tracked date stays and nothing but the RevalidationDue of the case is announced")
  void aFailComputesNoDate() throws Exception {
    providerWithOneCase();

    screeningEvents.onEvent(
        RevalidationFixtures.screeningCompleted(
            UUID.randomUUID(), NPI, "REVALIDATION", "FAIL", UUID.randomUUID()));

    assertThat(openCases()).isZero();
    assertThat(trackedDueDate()).isEqualTo("2026-02-28");
    assertThat(
            RevalidationFixtures.count(
                jdbc, "SELECT count(*) FROM outbox_event WHERE type <> 'RevalidationDue'"))
        .isZero();
  }
}
