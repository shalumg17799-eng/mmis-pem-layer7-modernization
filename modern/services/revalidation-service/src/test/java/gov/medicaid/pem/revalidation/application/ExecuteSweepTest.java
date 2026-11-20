package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The work of one sweep run (MAM-45, PRVREV01): every tracked provider is evaluated; each due
 * ACTIVE provider without an open case gets one DUE case and one RevalidationDue; the run records
 * its counts and its REVALQ file last (D-003, D-006, D-020, D-024, D-029). The store, the outbox
 * and the queue file are the ports; the class under test is the real ExecuteSweep.
 */
class ExecuteSweepTest {

  static final Instant NOW = Instant.parse("2026-03-31T08:00:00Z");
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);
  static final UUID RUN = UUID.fromString("7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44");
  static final byte[] FILE = {7, 8, 9};

  final InMemoryStore store = new InMemoryStore();
  final List<RevalidationCase> filed = new ArrayList<>();
  final QueueFile queueFile =
      opened -> {
        store.calls.add("file");
        filed.addAll(opened);
        return FILE;
      };
  final ExecuteSweep sweep =
      new ExecuteSweep(store, store, queueFile, store.outbox, Clock.fixed(NOW, ZoneOffset.UTC));

  static LocalDate day(int y, int m, int d) {
    return LocalDate.of(y, m, d);
  }

  // ---- AC-2: a due ACTIVE provider gets one DUE case with its own due date --------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-2] an ACTIVE provider whose due date has arrived gets one DUE case with its own due date")
  void dueProviderGetsOneCase() {
    store.track("1003000222", "A", day(2026, 2, 28));

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.cases.values())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.npi()).isEqualTo("1003000222");
              assertThat(c.status()).isEqualTo(CaseStatus.DUE);
              assertThat(c.dueDate()).isEqualTo(day(2026, 2, 28));
              assertThat(c.openedAt()).isEqualTo(NOW);
              assertThat(c.closedAt()).isNull();
              assertThat(c.caseId()).isNotNull();
            });
    assertThat(result).isEqualTo(new ExecuteSweep.Result(1, 1, 0));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-2] two due providers each get a case with their own due date, and the case is tied to the run")
  void twoDueProviders() {
    store.track("1003000222", "A", day(2026, 2, 28));
    store.track("1003000333", "A", day(2026, 3, 31));

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.cases.values())
        .extracting(RevalidationCase::npi, RevalidationCase::dueDate)
        .containsExactly(
            tuple("1003000222", day(2026, 2, 28)), tuple("1003000333", day(2026, 3, 31)));
    assertThat(store.openedByRun.values()).containsOnly(RUN);
    assertThat(result.queued()).isEqualTo(2);
    Set<UUID> ids = new HashSet<>();
    store.cases.values().forEach(c -> ids.add(c.caseId()));
    assertThat(ids).as("every case has its own id").hasSize(2);
  }

  @ParameterizedTest(
      name = "[MAM-45 AC-2] due {0} on the business date 2026-03-31: {1} case(s) opened")
  @CsvSource({
    "2026-03-30, 1", // yesterday
    "2026-03-31, 1", // today
    "2026-04-01, 0" // tomorrow
  })
  void dueDateEdges(LocalDate due, int opened) {
    store.track("1003000222", "A", due);

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.cases).hasSize(opened);
    assertThat(result.queued()).isEqualTo(opened);
    assertThat(result.evaluated()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-1] the due date is compared with the business date given, not with the clock")
  void businessDateNotClock() {
    store.track("1003000222", "A", day(2026, 3, 31));

    assertThat(sweep.execute(RUN, day(2026, 3, 30)).queued()).isZero();
    assertThat(store.cases).isEmpty();
    assertThat(sweep.execute(RUN, day(2026, 3, 31)).queued()).isEqualTo(1);
    assertThat(store.cases).hasSize(1);
  }

  // ---- AC-3: not due, not ACTIVE ---------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-45 AC-3] a provider with status {0} gets no case")
  @ValueSource(strings = {"P", "D", "T", "S"})
  void notActiveGetsNoCase(String status) {
    store.track("1003000444", status, day(2026, 2, 28));

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.cases).isEmpty();
    assertThat(store.events).isEmpty();
    assertThat(result).isEqualTo(new ExecuteSweep.Result(1, 0, 0));
  }

  // ---- AC-4: opening a case announces it -------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-4] opening a case writes one RevalidationDue keyed by the NPI with caseId, npi and dueDate")
  void announcesTheCase() {
    store.track("1003000222", "A", day(2026, 2, 28));

    sweep.execute(RUN, BUSINESS_DATE);

    RevalidationCase opened = store.cases.values().iterator().next();
    assertThat(store.events).hasSize(1);
    InMemoryStore.Event event = store.events.get(0);
    assertThat(event.type()).isEqualTo("RevalidationDue");
    assertThat(event.subject()).isEqualTo("1003000222");
    assertThat(event.data())
        .isEqualTo(new RevalidationDueData(opened.caseId(), "1003000222", day(2026, 2, 28)));
  }

  @Test
  @DisplayName("[MAM-45 AC-4] a case that is not opened is not announced")
  void nothingAnnouncedWithoutACase() {
    store.track("1003000777", "A", day(2026, 4, 1));
    store.track("1003000555", "T", day(2026, 2, 28));
    store.track("1003000666", "A", null);

    sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.events).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-4] a case is opened before its event is written, and every case before the run completes")
  void orderOfTheWork() {
    store.track("1003000222", "A", day(2026, 2, 28));
    store.track("1003000333", "A", day(2026, 3, 31));

    sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.calls)
        .containsExactly(
            "open:1003000222",
            "event:RevalidationDue:1003000222",
            "open:1003000333",
            "event:RevalidationDue:1003000333",
            "file",
            "complete");
  }

  // ---- AC-5: an open case is not queued again --------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-45 AC-5] a provider with a {0} case is not queued again and nothing is announced")
  @ValueSource(strings = {"DUE", "IN_SCREENING"})
  void openCaseIsNotQueuedAgain(CaseStatus open) {
    store.track("1003000222", "A", day(2026, 2, 28));
    RevalidationCase existing = store.addCase("1003000222", open, day(2026, 2, 28));

    ExecuteSweep.Result result = sweep.execute(RUN, day(2026, 4, 30));

    assertThat(store.cases).containsOnlyKeys(existing.caseId());
    assertThat(store.events).isEmpty();
    assertThat(filed).isEmpty();
    assertThat(result).isEqualTo(new ExecuteSweep.Result(1, 0, 0));
  }

  @ParameterizedTest(
      name = "[MAM-49 AC-4] a {0} case does not stop a new case for a provider that is due again")
  @ValueSource(strings = {"COMPLETED", "CANCELLED"})
  void closedCaseDoesNotBlock(CaseStatus closed) {
    store.track("1003000222", "A", day(2026, 2, 28));
    RevalidationCase old = store.addCase("1003000222", closed, day(2026, 2, 28));

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.cases).hasSize(2);
    assertThat(store.caseOf(old.caseId()).status()).isEqualTo(closed);
    assertThat(store.cases.values())
        .filteredOn(c -> c.status().open())
        .singleElement()
        .satisfies(c -> assertThat(c.npi()).isEqualTo("1003000222"));
    assertThat(result.queued()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-5] the open-case check is made per provider: only the provider with a case is skipped")
  void openCaseCheckIsPerProvider() {
    store.track("1003000222", "A", day(2026, 2, 28));
    store.track("1003000333", "A", day(2026, 2, 28));
    store.addCase("1003000222", CaseStatus.DUE, day(2026, 2, 28));

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(result.queued()).isEqualTo(1);
    assertThat(store.events).extracting(InMemoryStore.Event::subject).containsExactly("1003000333");
    assertThat(filed).extracting(RevalidationCase::npi).containsExactly("1003000333");
  }

  // ---- AC-6: no due date -----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-6] an ACTIVE provider with no due date opens no case and is counted under noDueDate")
  void noDueDateIsCounted() {
    store.track("1003000666", "A", null);

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(store.cases).isEmpty();
    assertThat(filed).isEmpty();
    assertThat(result).isEqualTo(new ExecuteSweep.Result(1, 0, 1));
    assertThat(store.completions)
        .singleElement()
        .satisfies(c -> assertThat(c.noDueDate()).isEqualTo(1));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-6] a provider that is not ACTIVE and has no due date is not counted under noDueDate")
  void inactiveWithoutDueDateIsNotCounted() {
    store.track("1003000666", "S", null);

    assertThat(sweep.execute(RUN, BUSINESS_DATE).noDueDate()).isZero();
  }

  // ---- AC-7 and AC-8: the file, the counts, the result -----------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-7] the queue file is written from exactly the cases this run opened, not from earlier cases")
  void fileHoldsOnlyTheOpenedCases() {
    store.track("1003000222", "A", day(2026, 2, 28));
    store.track("1003000333", "A", day(2026, 3, 31));
    store.addCase("1003000333", CaseStatus.IN_SCREENING, day(2026, 3, 31));
    store.addCase("1003000999", CaseStatus.DUE, day(2026, 1, 1));

    sweep.execute(RUN, BUSINESS_DATE);

    assertThat(filed).extracting(RevalidationCase::npi).containsExactly("1003000222");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] the run completes with its counts, its file and the end time, after every provider was evaluated")
  void completesTheRun() {
    store.track("1003000222", "A", day(2026, 2, 28)); // queued
    store.track("1003000333", "A", day(2026, 3, 31)); // queued
    store.track("1003000444", "S", day(2026, 2, 28)); // not active
    store.track("1003000555", "T", null); // not active
    store.track("1003000666", "A", null); // no due date
    store.track("1003000777", "A", day(2026, 4, 1)); // not due
    store.addCase("1003000888", CaseStatus.DUE, day(2026, 1, 1));
    store.track("1003000888", "A", day(2026, 1, 1)); // has an open case

    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(result).isEqualTo(new ExecuteSweep.Result(7, 2, 1));
    assertThat(store.completions).hasSize(1);
    InMemoryStore.Completion c = store.completions.get(0);
    assertThat(c.runId()).isEqualTo(RUN);
    assertThat(c.queued()).isEqualTo(2);
    assertThat(c.noDueDate()).isEqualTo(1);
    assertThat(c.file()).isEqualTo(FILE);
    assertThat(c.endedAt()).isEqualTo(NOW);
    assertThat(store.failures).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a sweep with no tracked provider completes with 0 evaluated, 0 queued, 0 noDueDate")
  void emptySweep() {
    ExecuteSweep.Result result = sweep.execute(RUN, BUSINESS_DATE);

    assertThat(result).isEqualTo(new ExecuteSweep.Result(0, 0, 0));
    assertThat(store.completions).hasSize(1);
    assertThat(store.calls).containsExactly("file", "complete");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a failure while opening a case ends the sweep: the run is never completed")
  void failureOnOpenNeverCompletes() {
    store.track("1003000222", "A", day(2026, 2, 28));
    store.failOnOpen = new IllegalStateException("SYNTHETIC DISK FULL");

    assertThatThrownBy(() -> sweep.execute(RUN, BUSINESS_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SYNTHETIC DISK FULL");

    assertThat(store.completions).isEmpty();
    assertThat(store.events).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a failure while writing the event ends the sweep: the run is never completed")
  void failureOnEventNeverCompletes() {
    store.track("1003000222", "A", day(2026, 2, 28));
    store.failOnEvent = new IllegalStateException("SYNTHETIC OUTBOX DOWN");

    assertThatThrownBy(() -> sweep.execute(RUN, BUSINESS_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SYNTHETIC OUTBOX DOWN");

    assertThat(store.completions).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a run whose job did not complete is recorded FAILED with the reason and the end time")
  void failedRunIsRecorded() {
    sweep.failed(RUN, BUSINESS_DATE, "SYNTHETIC REASON");

    assertThat(store.failures)
        .containsExactly(new InMemoryStore.Failure(RUN, "SYNTHETIC REASON", NOW));
    assertThat(store.completions).isEmpty();
  }
}
