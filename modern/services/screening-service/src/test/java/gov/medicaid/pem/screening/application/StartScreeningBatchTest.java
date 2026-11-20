package gov.medicaid.pem.screening.application;

import static gov.medicaid.pem.screening.application.Fakes.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.application.Fakes.Outbox;
import gov.medicaid.pem.screening.application.Fakes.RunsFake;
import gov.medicaid.pem.screening.application.Fakes.Store;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-32 AC-1 and AC-6: starting a run records it RUNNING and launches its job for the explicit
 * business date (D-020); a job that cannot be started is recorded FAILED with its reason, never
 * silently dropped (D-024).
 */
class StartScreeningBatchTest {

  static final LocalDate DATE = LocalDate.of(2026, 3, 31);

  record Launch(UUID runId, LocalDate date) {}

  final RunsFake runs = new RunsFake();
  final List<Launch> launches = new ArrayList<>();
  RuntimeException launchFailure;

  final ScreenProvider screen =
      new ScreenProvider(
          Fakes::facts,
          facts -> Fakes.results("P", "P", "P", "P"),
          new Store(),
          new Outbox(),
          CLOCK);
  final RunScreeningBatch batch =
      new RunScreeningBatch(runs, screen, (screened, date) -> new byte[0], CLOCK);
  final ScreeningBatchLauncher launcher =
      (runId, date) -> {
        if (launchFailure != null) {
          throw launchFailure;
        }
        launches.add(new Launch(runId, date));
      };
  final StartScreeningBatch start = new StartScreeningBatch(batch, launcher, runs);

  @Test
  @DisplayName("[MAM-32 AC-1] the answer is the RUNNING run for the business date")
  void running() {
    ScreeningRuns.Run run = start.start(DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.businessDate()).isEqualTo(DATE);
    assertThat(run.error()).isNull();
  }

  @Test
  @DisplayName("[MAM-32 AC-1] the job is launched once, with the run id and the business date")
  void launched() {
    ScreeningRuns.Run run = start.start(DATE);
    assertThat(launches).containsExactly(new Launch(run.runId(), DATE));
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a job that cannot be launched leaves a FAILED run with the reason")
  void launchFails() {
    launchFailure = new IllegalStateException("job PRVSCR01 not started: repository is down");
    ScreeningRuns.Run run = start.start(DATE);
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("job PRVSCR01 not started: repository is down");
    assertThat(run.endedAt()).isEqualTo(Fakes.NOW);
    assertThat(run.read()).isZero();
    assertThat(launches).isEmpty();
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a launch failure without a message is recorded with the exception text")
  void launchFailsWithoutMessage() {
    launchFailure = new IllegalStateException();
    ScreeningRuns.Run run = start.start(DATE);
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  @DisplayName("[MAM-32 AC-6] the failed run is the one that was opened, not a second run")
  void sameRunFails() {
    launchFailure = new IllegalStateException("x");
    ScreeningRuns.Run run = start.start(DATE);
    assertThat(runs.runs).containsOnlyKeys(run.runId());
  }
}
