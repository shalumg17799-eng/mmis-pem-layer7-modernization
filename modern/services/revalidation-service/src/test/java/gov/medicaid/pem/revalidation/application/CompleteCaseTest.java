package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Completing a revalidation case (MAM-48, D-029): on request with a screening result by an analyst
 * or the system, or by itself when a REVALIDATION ScreeningCompleted arrives for the provider's
 * open case. PASS and FAIL both close it (question C-12 answer A); the next due date is never set
 * here (D-003, C-10: provider-service owns it).
 */
class CompleteCaseTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  static final LocalDate DUE = LocalDate.of(2026, 2, 28);

  final InMemoryStore store = new InMemoryStore();
  final CompleteCase complete = new CompleteCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

  // ---- AC-2: complete on request ---------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-48 AC-2] a {0} case is COMPLETED by a {1} screening result")
  @CsvSource({
    "DUE, PASS",
    "IN_SCREENING, PASS",
    "DUE, FAIL",
    "IN_SCREENING, FAIL",
  })
  void completesAnOpenCase(CaseStatus stage, String result) {
    RevalidationCase c = store.addCase("1003000222", stage, DUE);
    UUID screening = UUID.randomUUID();

    RevalidationCase done = complete.byRequest(c.caseId(), screening, result);

    assertThat(done)
        .isEqualTo(
            new RevalidationCase(
                c.caseId(), "1003000222", DUE, CaseStatus.COMPLETED, c.openedAt(), NOW));
    assertThat(store.caseOf(c.caseId())).isEqualTo(done);
    assertThat(store.closings)
        .containsExactly(
            new InMemoryStore.Closing(c.caseId(), CaseStatus.COMPLETED, screening, NOW));
  }

  @Test
  @DisplayName("[MAM-48 AC-2] completing a case does not touch the tracked due date (D-003)")
  void theDueDateStaysWithProviderService() {
    store.track("1003000222", "A", DUE);
    RevalidationCase c = store.addCase("1003000222", CaseStatus.DUE, DUE);

    complete.byRequest(c.caseId(), UUID.randomUUID(), "PASS");

    assertThat(store.tracked.get("1003000222").dueDate()).isEqualTo(DUE);
    assertThat(store.calls).containsExactly("close:COMPLETED");
    assertThat(store.events).isEmpty();
  }

  // ---- AC-3: unknown and closed cases ----------------------------------------------------------

  @Test
  @DisplayName("[MAM-48 AC-3] an unknown case is a 404 and nothing is changed")
  void unknownCase() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> complete.byRequest(unknown, UUID.randomUUID(), "PASS"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(404);
              assertThat(e.getMessage()).contains(unknown.toString());
            });
    assertThat(store.closings).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-48 AC-3] a {0} case is a 409 and stays unchanged")
  @ValueSource(strings = {"COMPLETED", "CANCELLED"})
  void closedCaseIsRefused(CaseStatus closed) {
    RevalidationCase c = store.addCase("1003000222", closed, DUE);

    assertThatThrownBy(() -> complete.byRequest(c.caseId(), UUID.randomUUID(), "PASS"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(409);
              assertThat(e.getMessage()).contains(c.caseId().toString()).contains(closed.name());
            });
    assertThat(store.caseOf(c.caseId())).isEqualTo(c);
    assertThat(store.closings).isEmpty();
  }

  // ---- AC-4 and AC-5: a REVALIDATION screening closes the open case ----------------------------

  @ParameterizedTest(
      name = "[MAM-48 AC-4] a REVALIDATION screening with result {0} completes the {1} case")
  @CsvSource({
    "PASS, DUE",
    "PASS, IN_SCREENING",
    "FAIL, DUE",
    "FAIL, IN_SCREENING",
  })
  void revalidationScreeningCompletesTheCase(String result, CaseStatus stage) {
    RevalidationCase c = store.addCase("1003000222", stage, DUE);
    UUID screening = UUID.randomUUID();

    boolean closed = complete.byScreening("1003000222", "REVALIDATION", result, screening);

    assertThat(closed).isTrue();
    assertThat(store.caseOf(c.caseId()).status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(store.caseOf(c.caseId()).closedAt()).isEqualTo(NOW);
    assertThat(store.closings)
        .containsExactly(
            new InMemoryStore.Closing(c.caseId(), CaseStatus.COMPLETED, screening, NOW));
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-5] a FAILED re-screening closes the case without moving the due date or announcing anything")
  void failedRescreeningKeepsTheDueDate() {
    store.track("1003000222", "A", DUE);
    store.addCase("1003000222", CaseStatus.DUE, DUE);

    complete.byScreening("1003000222", "REVALIDATION", "FAIL", UUID.randomUUID());

    assertThat(store.tracked.get("1003000222").dueDate()).isEqualTo(DUE);
    assertThat(store.events).isEmpty();
    assertThat(store.cases.values()).singleElement().satisfies(
        c -> assertThat(c.dueDate()).isEqualTo(DUE));
  }

  @Test
  @DisplayName("[MAM-48 AC-4] only the case of the screened provider is completed")
  void onlyTheScreenedProvider() {
    RevalidationCase mine = store.addCase("1003000222", CaseStatus.DUE, DUE);
    RevalidationCase other = store.addCase("1003000333", CaseStatus.DUE, DUE);

    complete.byScreening("1003000222", "REVALIDATION", "PASS", UUID.randomUUID());

    assertThat(store.caseOf(mine.caseId()).status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(store.caseOf(other.caseId())).isEqualTo(other);
  }

  // ---- AC-6: only revalidation screenings close cases ------------------------------------------

  @ParameterizedTest(name = "[MAM-48 AC-6] a {0} screening closes nothing")
  @ValueSource(strings = {"INITIAL", "MANUAL", "revalidation", "Revalidation", ""})
  void otherReasonsCloseNothing(String reason) {
    RevalidationCase c = store.addCase("1003000222", CaseStatus.DUE, DUE);

    boolean closed = complete.byScreening("1003000222", reason, "PASS", UUID.randomUUID());

    assertThat(closed).isFalse();
    assertThat(store.caseOf(c.caseId())).isEqualTo(c);
    assertThat(store.closings).isEmpty();
  }

  @Test
  @DisplayName("[MAM-48 AC-6] a screening without a reason closes nothing")
  void noReasonClosesNothing() {
    RevalidationCase c = store.addCase("1003000222", CaseStatus.DUE, DUE);

    assertThat(complete.byScreening("1003000222", null, "PASS", UUID.randomUUID())).isFalse();
    assertThat(store.caseOf(c.caseId())).isEqualTo(c);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-6] a REVALIDATION screening of a provider without an open case is ignored and opens no case")
  void noOpenCaseIsIgnored() {
    RevalidationCase other = store.addCase("1003000222", CaseStatus.DUE, DUE);

    boolean closed = complete.byScreening("1003000999", "REVALIDATION", "PASS", UUID.randomUUID());

    assertThat(closed).isFalse();
    assertThat(store.cases).containsOnlyKeys(other.caseId());
    assertThat(store.closings).isEmpty();
  }

  @ParameterizedTest(
      name = "[MAM-49 AC-3] a late REVALIDATION result does not reopen a {0} case and raises no error")
  @ValueSource(strings = {"CANCELLED", "COMPLETED"})
  void lateResultDoesNotReopen(CaseStatus closed) {
    RevalidationCase c = store.addCase("1003000222", closed, DUE);

    boolean changed = complete.byScreening("1003000222", "REVALIDATION", "PASS", UUID.randomUUID());

    assertThat(changed).isFalse();
    assertThat(store.caseOf(c.caseId())).isEqualTo(c);
    assertThat(store.cases).hasSize(1);
  }
}
