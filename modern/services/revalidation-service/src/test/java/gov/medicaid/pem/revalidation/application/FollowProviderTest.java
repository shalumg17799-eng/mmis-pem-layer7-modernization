package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ProviderStatusChanged (D-014, MAM-45, MAM-49): the tracked status and due date always follow the
 * event, and a provider that is no longer ACTIVE cancels its open case (question C-14 answer A,
 * D-006, D-029).
 */
class FollowProviderTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  static final LocalDate DUE = LocalDate.of(2026, 2, 28);

  final InMemoryStore store = new InMemoryStore();
  final FollowProvider follow = new FollowProvider(store, store, Clock.fixed(NOW, ZoneOffset.UTC));

  // ---- the read model follows ------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-45 AC-3] the tracked status and due date are set from the event")
  void tracksStatusAndDueDate() {
    follow.statusChanged(new TrackedProvider("1003000222", "A", LocalDate.of(2031, 3, 31)));

    assertThat(store.tracked)
        .containsOnlyKeys("1003000222")
        .containsEntry(
            "1003000222", new TrackedProvider("1003000222", "A", LocalDate.of(2031, 3, 31)));
  }

  @Test
  @DisplayName("[MAM-45 AC-6] a due date that is null in the event is tracked as no due date")
  void nullDueDateIsTracked() {
    follow.statusChanged(new TrackedProvider("1003000222", "A", null));

    assertThat(store.tracked.get("1003000222").dueDate()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-7] a later event moves the tracked due date forward (A to A, 2031-03-31)")
  void laterEventMovesTheDate() {
    follow.statusChanged(new TrackedProvider("1003000222", "A", DUE));
    follow.statusChanged(new TrackedProvider("1003000222", "A", LocalDate.of(2031, 3, 31)));

    assertThat(store.tracked.get("1003000222").dueDate()).isEqualTo(LocalDate.of(2031, 3, 31));
    assertThat(store.tracked).hasSize(1);
  }

  // ---- MAM-49 AC-2: leaving ACTIVE cancels the open case ---------------------------------------

  @ParameterizedTest(name = "[MAM-49 AC-2] a provider that becomes {1} cancels its {0} case")
  @CsvSource({
    "DUE, S",
    "DUE, T",
    "DUE, D",
    "IN_SCREENING, S",
    "IN_SCREENING, T",
    "IN_SCREENING, D",
  })
  void leavingActiveCancelsTheOpenCase(CaseStatus stage, String status) {
    RevalidationCase c = store.addCase("1003000222", stage, DUE);

    follow.statusChanged(new TrackedProvider("1003000222", status, DUE));

    assertThat(store.caseOf(c.caseId()))
        .isEqualTo(
            new RevalidationCase(
                c.caseId(), "1003000222", DUE, CaseStatus.CANCELLED, c.openedAt(), NOW));
    assertThat(store.closings)
        .containsExactly(new InMemoryStore.Closing(c.caseId(), CaseStatus.CANCELLED, null, NOW));
  }

  @Test
  @DisplayName("[MAM-49 AC-2] the read model is updated before the case is cancelled")
  void trackedBeforeCancelled() {
    store.addCase("1003000222", CaseStatus.DUE, DUE);

    follow.statusChanged(new TrackedProvider("1003000222", "S", DUE));

    assertThat(store.calls).containsExactly("track:1003000222", "close:CANCELLED");
    assertThat(store.tracked.get("1003000222").enrollStatus()).isEqualTo("S");
  }

  @Test
  @DisplayName("[MAM-49 AC-2] only the case of the provider that left ACTIVE is cancelled")
  void onlyThatProvidersCase() {
    RevalidationCase mine = store.addCase("1003000222", CaseStatus.DUE, DUE);
    RevalidationCase other = store.addCase("1003000333", CaseStatus.DUE, DUE);

    follow.statusChanged(new TrackedProvider("1003000222", "S", DUE));

    assertThat(store.caseOf(mine.caseId()).status()).isEqualTo(CaseStatus.CANCELLED);
    assertThat(store.caseOf(other.caseId())).isEqualTo(other);
  }

  @ParameterizedTest(
      name = "[MAM-49 AC-2] a {0} case is not touched when its provider leaves ACTIVE")
  @ValueSource(strings = {"COMPLETED", "CANCELLED"})
  void closedCasesAreNotTouched(CaseStatus closed) {
    RevalidationCase c = store.addCase("1003000222", closed, DUE);

    follow.statusChanged(new TrackedProvider("1003000222", "T", DUE));

    assertThat(store.caseOf(c.caseId())).isEqualTo(c);
    assertThat(store.closings).isEmpty();
  }

  @Test
  @DisplayName("[MAM-49 AC-2] a status change of a provider without an open case opens nothing")
  void noOpenCaseOpensNothing() {
    follow.statusChanged(new TrackedProvider("1003000444", "S", DUE));

    assertThat(store.cases).isEmpty();
    assertThat(store.closings).isEmpty();
    assertThat(store.tracked).containsOnlyKeys("1003000444");
  }

  // ---- a provider that stays ACTIVE keeps its case ---------------------------------------------

  @ParameterizedTest(name = "[MAM-49 AC-2] a provider that is ACTIVE keeps its {0} case")
  @ValueSource(strings = {"DUE", "IN_SCREENING"})
  void activeKeepsTheCase(CaseStatus stage) {
    RevalidationCase c = store.addCase("1003000222", stage, DUE);

    follow.statusChanged(new TrackedProvider("1003000222", "A", LocalDate.of(2031, 3, 31)));

    assertThat(store.caseOf(c.caseId())).isEqualTo(c);
    assertThat(store.closings).isEmpty();
  }

  @Test
  @DisplayName("[MAM-49 AC-4] a reinstated provider is tracked as ACTIVE again with its due date")
  void reinstatedProvider() {
    store.addCase("1003000222", CaseStatus.CANCELLED, DUE);

    follow.statusChanged(new TrackedProvider("1003000222", "A", DUE));

    assertThat(store.tracked.get("1003000222"))
        .isEqualTo(new TrackedProvider("1003000222", "A", DUE));
    assertThat(store.cases).hasSize(1);
    assertThat(store.closings).isEmpty();
  }
}
