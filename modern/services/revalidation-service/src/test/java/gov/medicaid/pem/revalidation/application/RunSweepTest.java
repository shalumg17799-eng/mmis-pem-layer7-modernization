package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * startRevalidationSweep (MAM-45, PRVMTH01 STEP010): a RUNNING run for an explicit business date
 * (D-020) is recorded, then its Spring Batch job is started in the background (ADR-004, Q-102 A); a
 * run that cannot be started is recorded FAILED with the reason, never lost (D-024).
 */
class RunSweepTest {

  static final Instant NOW = Instant.parse("2026-03-31T08:00:00Z");
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);

  record Launch(UUID runId, LocalDate businessDate) {}

  final InMemoryStore store = new InMemoryStore();
  final List<Launch> launches = new ArrayList<>();
  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  RunSweep sweepWith(SweepJobLauncher launcher) {
    return new RunSweep(store, launcher, clock);
  }

  final RunSweep sweep =
      sweepWith(
          (runId, businessDate) -> {
            store.calls.add("launch");
            launches.add(new Launch(runId, businessDate));
          });

  @Test
  @DisplayName(
      "[MAM-45 AC-1] starting a sweep answers a RUNNING run for the business date with zero counts and no end")
  void answersARunningRun() {
    SweepRun run = sweep.start(BUSINESS_DATE);

    assertThat(run.runId()).isNotNull();
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.queued()).isZero();
    assertThat(run.noDueDate()).isZero();
    assertThat(run.error()).isNull();
    assertThat(run.startedAt()).isEqualTo(NOW);
    assertThat(run.endedAt()).isNull();
  }

  @Test
  @DisplayName("[MAM-45 AC-1] the RUNNING run is recorded before its job is started")
  void recordsTheRunThenStartsTheJob() {
    SweepRun run = sweep.start(BUSINESS_DATE);

    assertThat(store.created).containsExactly(run);
    assertThat(store.calls).containsExactly("create", "launch");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-1] the job gets the run id and the explicit business date as its parameters (D-020)")
  void jobParameters() {
    SweepRun run = sweep.start(LocalDate.of(2031, 3, 31));

    assertThat(launches).containsExactly(new Launch(run.runId(), LocalDate.of(2031, 3, 31)));
  }

  @Test
  @DisplayName("[MAM-45 AC-1] every start is its own run with its own id")
  void everyStartIsItsOwnRun() {
    SweepRun first = sweep.start(BUSINESS_DATE);
    SweepRun second = sweep.start(BUSINESS_DATE);

    assertThat(first.runId()).isNotEqualTo(second.runId());
    assertThat(launches).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a job that cannot be started leaves a FAILED run with the reason (D-024)")
  void jobThatCannotBeStartedIsRecordedFailed() {
    RunSweep failing =
        sweepWith(
            (runId, businessDate) -> {
              throw new IllegalStateException("job revalidation-sweep not started: pool down");
            });

    SweepRun run = failing.start(BUSINESS_DATE);

    assertThat(store.failures)
        .containsExactly(
            new InMemoryStore.Failure(
                run.runId(), "job revalidation-sweep not started: pool down", NOW));
    assertThat(store.created).containsExactly(run);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a launch failure without a message is recorded with the exception's name")
  void failureWithoutMessage() {
    RunSweep failing =
        sweepWith(
            (runId, businessDate) -> {
              throw new IllegalStateException();
            });

    SweepRun run = failing.start(BUSINESS_DATE);

    assertThat(store.failures)
        .containsExactly(
            new InMemoryStore.Failure(run.runId(), "java.lang.IllegalStateException", NOW));
  }

  @Test
  @DisplayName("[MAM-45 AC-1] a job that starts leaves nothing failed")
  void noFailureWhenTheJobStarts() {
    sweep.start(BUSINESS_DATE);

    assertThat(store.failures).isEmpty();
  }

  @Test
  @DisplayName("[MAM-45 AC-8] the RUNNING marker of a run is the text RUNNING")
  void runningMarker() {
    assertThat(SweepRun.RUNNING).isEqualTo("RUNNING");
  }
}
