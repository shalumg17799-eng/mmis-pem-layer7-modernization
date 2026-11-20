package gov.medicaid.pem.directory.application;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * MAM-67 AC-1 and AC-7: starting an extract records the run, runs its job to the end and answers
 * with the finished run, COMPLETED or FAILED; a job that cannot even be started is a FAILED run
 * with the reason (D-024).
 */
class StartDirectoryExtractTest {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-03-31");

  final Map<UUID, DirectoryExtracts.Run> runs = new HashMap<>();
  final List<String> launched = new ArrayList<>();
  boolean runVanishes;

  final DirectoryExtracts extracts =
      new DirectoryExtracts() {
        @Override
        public void create(Run run) {
          runs.put(run.runId(), run);
        }

        @Override
        public void complete(UUID runId, int rows, byte[] file, Instant endedAt) {
          Run r = runs.get(runId);
          runs.put(
              runId,
              new Run(runId, r.businessDate(), Run.COMPLETED, rows, null, r.startedAt(), endedAt));
        }

        @Override
        public void fail(UUID runId, String error, Instant endedAt) {
          Run r = runs.get(runId);
          runs.put(
              runId,
              new Run(
                  runId, r.businessDate(), Run.FAILED, r.rows(), error, r.startedAt(), endedAt));
        }

        @Override
        public Optional<Run> find(UUID runId) {
          return runVanishes ? Optional.empty() : Optional.ofNullable(runs.get(runId));
        }

        @Override
        public Optional<byte[]> file(UUID runId) {
          return Optional.empty();
        }
      };

  final DirectoryStore store =
      new DirectoryStore() {
        @Override
        public Optional<DirectoryEntry> find(String npi) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void save(DirectoryEntry entry) {
          throw new UnsupportedOperationException();
        }

        @Override
        public DirectoryPage published(
            String name, String providerType, String taxonomy, int page, int size) {
          return new DirectoryPage(
              List.of(
                  new DirectoryEntry(
                      "1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", "207Q00000X")),
              page,
              size,
              1);
        }
      };

  final RunDirectoryExtract extract =
      new RunDirectoryExtract(extracts, store, entries -> new byte[entries.size() * 100], CLOCK);

  StartDirectoryExtract start(DirectoryExtractJobLauncher launcher) {
    return new StartDirectoryExtract(extract, launcher, extracts);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] the answer is the run as the finished job left it: COMPLETED with its rows and end time")
  void finishedRun() {
    StartDirectoryExtract start =
        start(
            (runId, date) -> {
              launched.add(runId + "@" + date);
              extract.execute(runId, date);
            });
    DirectoryExtracts.Run run = start.start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.rows()).isEqualTo(1);
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.startedAt()).isEqualTo(NOW);
    assertThat(run.endedAt()).isEqualTo(NOW);
    assertThat(run.error()).isNull();
  }

  @Test
  @DisplayName("[MAM-67 AC-1] the job is started once, for the new run and the given business date")
  void launchedOnce() {
    StartDirectoryExtract start = start((runId, date) -> launched.add(runId + "@" + date));
    DirectoryExtracts.Run run = start.start(BUSINESS_DATE);
    assertThat(launched).containsExactly(run.runId() + "@" + BUSINESS_DATE);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] a job that fails (the listener marks the run FAILED) is answered as FAILED with its reason")
  void failedByTheJob() {
    StartDirectoryExtract start =
        start((runId, date) -> extract.failed(runId, date, "the database rejected the write"));
    DirectoryExtracts.Run run = start.start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("the database rejected the write");
    assertThat(run.endedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] a job that cannot be started is recorded FAILED with the exception message")
  void launcherThrows() {
    StartDirectoryExtract start =
        start(
            (runId, date) -> {
              throw new IllegalStateException("job PRVDIR01 not started: no job repository");
            });
    DirectoryExtracts.Run run = start.start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("job PRVDIR01 not started: no job repository");
    assertThat(run.endedAt()).isEqualTo(NOW);
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] an exception without a message is recorded by its type, so the reason is never empty")
  void launcherThrowsWithoutMessage() {
    StartDirectoryExtract start =
        start(
            (runId, date) -> {
              throw new IllegalStateException();
            });
    DirectoryExtracts.Run run = start.start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] a run that is still RUNNING when the job returns is answered as it is")
  void stillRunning() {
    DirectoryExtracts.Run run = start((runId, date) -> {}).start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.endedAt()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] if the stored run cannot be read back, the run that was opened is answered")
  void storedRunMissing() {
    StartDirectoryExtract start = start((runId, date) -> runVanishes = true);
    DirectoryExtracts.Run run = start.start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.runId()).isNotNull();
  }
}
