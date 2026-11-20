package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * MAM-45: a sweep run opens one case per due ACTIVE provider without an open case, announces each,
 * writes the REVALQ file of the cases it opened and records its result; a failure is recorded as
 * FAILED, never COMPLETED (D-024).
 */
class RunSweepTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);

  record Sent(String type, String subject, Object data) {}

  final Map<UUID, SweepRun> runs = new HashMap<>();
  final Map<UUID, byte[]> files = new HashMap<>();
  final List<TrackedProvider> tracked = new ArrayList<>();
  final List<RevalidationCase> opened = new ArrayList<>();
  final List<String> withOpenCase = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  RuntimeException openFailure;

  final SweepRuns runStore =
      new SweepRuns() {
        @Override
        public void create(SweepRun run) {
          runs.put(run.runId(), run);
        }

        @Override
        public void complete(UUID runId, int queued, int noDueDate, byte[] file, Instant endedAt) {
          SweepRun r = runs.get(runId);
          runs.put(
              runId,
              new SweepRun(
                  runId, r.businessDate(), "COMPLETED", queued, noDueDate, null, r.startedAt(),
                  endedAt));
          files.put(runId, file);
        }

        @Override
        public void fail(UUID runId, String error, Instant endedAt) {
          SweepRun r = runs.get(runId);
          runs.put(
              runId,
              new SweepRun(
                  runId, r.businessDate(), "FAILED", 0, 0, error, r.startedAt(), endedAt));
        }

        @Override
        public Optional<SweepRun> find(UUID runId) {
          return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public Optional<byte[]> queueFile(UUID runId) {
          return Optional.ofNullable(files.get(runId));
        }
      };

  final CaseStore caseStore =
      new CaseStore() {
        @Override
        public List<TrackedProvider> trackedProviders() {
          return List.copyOf(tracked);
        }

        @Override
        public boolean hasOpenCase(String npi) {
          return withOpenCase.contains(npi);
        }

        @Override
        public void open(RevalidationCase c, UUID runId) {
          if (openFailure != null) {
            throw openFailure;
          }
          opened.add(c);
          withOpenCase.add(c.npi());
        }

        @Override
        public CasePage find(String npi, CaseStatus status, int page, int size) {
          return new CasePage(List.copyOf(opened), page, size, opened.size());
        }
      };

  final RunSweep sweep =
      new RunSweep(
          runStore,
          caseStore,
          cases -> ("FILE:" + cases.size()).getBytes(),
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          TransactionOperations.withoutTransaction(),
          Runnable::run,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName(
      "[MAM-45 AC-1] a sweep starts as a RUNNING run for the business date given, not the clock date")
  void startsRunning() {
    SweepRun run = sweep.start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.startedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-2/AC-4/AC-8] each due ACTIVE provider gets one DUE case with its own due date, one RevalidationDue, and the run COMPLETES with the count")
  void opensCases() {
    tracked.add(new TrackedProvider("1003000222", "A", LocalDate.of(2026, 2, 28)));
    tracked.add(new TrackedProvider("1003000333", "A", LocalDate.of(2026, 3, 31)));
    tracked.add(new TrackedProvider("1003000777", "A", LocalDate.of(2026, 4, 1)));
    SweepRun run = sweep.start(BUSINESS_DATE);
    assertThat(opened)
        .extracting(RevalidationCase::npi, RevalidationCase::dueDate, RevalidationCase::status)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                "1003000222", LocalDate.of(2026, 2, 28), CaseStatus.DUE),
            org.assertj.core.groups.Tuple.tuple(
                "1003000333", LocalDate.of(2026, 3, 31), CaseStatus.DUE));
    assertThat(sent).hasSize(2);
    for (Sent s : sent) {
      RevalidationDueData d = (RevalidationDueData) s.data();
      assertThat(s.type()).isEqualTo("RevalidationDue");
      assertThat(s.subject()).isEqualTo(d.npi());
      RevalidationCase c =
          opened.stream().filter(o -> o.npi().equals(d.npi())).findFirst().orElseThrow();
      assertThat(d.caseId()).isEqualTo(c.caseId());
      assertThat(d.dueDate()).isEqualTo(c.dueDate());
    }
    SweepRun done = runs.get(run.runId());
    assertThat(done.status()).isEqualTo("COMPLETED");
    assertThat(done.queued()).isEqualTo(2);
    assertThat(done.endedAt()).isEqualTo(NOW);
    assertThat(new String(files.get(run.runId()))).isEqualTo("FILE:2");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-5] a provider with an open case is not queued again, announced again or written again")
  void openCaseIsNotRequeued() {
    tracked.add(new TrackedProvider("1003000222", "A", LocalDate.of(2026, 2, 28)));
    withOpenCase.add("1003000222");
    SweepRun run = sweep.start(BUSINESS_DATE);
    assertThat(opened).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(runs.get(run.runId()).queued()).isZero();
    assertThat(new String(files.get(run.runId()))).isEqualTo("FILE:0");
  }

  @Test
  @DisplayName("[MAM-45 AC-6] an ACTIVE provider without a due date is counted under noDueDate")
  void noDueDateCounted() {
    tracked.add(new TrackedProvider("1003000666", "A", null));
    SweepRun run = sweep.start(BUSINESS_DATE);
    assertThat(opened).isEmpty();
    assertThat(runs.get(run.runId()).noDueDate()).isEqualTo(1);
    assertThat(runs.get(run.runId()).status()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a run whose database write fails is FAILED with the reason, never COMPLETED")
  void failureIsVisible() {
    tracked.add(new TrackedProvider("1003000222", "A", LocalDate.of(2026, 2, 28)));
    openFailure = new IllegalStateException("database unavailable");
    SweepRun run = sweep.start(BUSINESS_DATE);
    SweepRun done = runs.get(run.runId());
    assertThat(done.status()).isEqualTo("FAILED");
    assertThat(done.error()).contains("database unavailable");
    assertThat(done.endedAt()).isEqualTo(NOW);
    assertThat(files).doesNotContainKey(run.runId());
  }
}
