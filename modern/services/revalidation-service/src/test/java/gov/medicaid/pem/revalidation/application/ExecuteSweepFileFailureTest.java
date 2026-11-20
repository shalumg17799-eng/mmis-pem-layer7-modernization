package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.io.UncheckedIOException;
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
 * A sweep whose REVALQ file cannot be written (MAM-45 AC-8: "a run whose database or file write
 * fails", D-024): the exception reaches the job, the run is never recorded as COMPLETED and no
 * count is reported for it. The class under test is the real ExecuteSweep; the file writer is the
 * port that fails.
 */
class ExecuteSweepFileFailureTest {

  static final Instant NOW = Instant.parse("2026-03-31T08:00:00Z");
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);
  static final UUID RUN = UUID.fromString("7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44");

  final InMemoryStore store = new InMemoryStore();

  private ExecuteSweep sweepWith(QueueFile queueFile) {
    return new ExecuteSweep(
        store, store, queueFile, store.outbox, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a failure while the REVALQ file is written ends the sweep with that failure and the run is never completed")
  void fileFailureNeverCompletesTheRun() {
    store.track("1003000222", "A", LocalDate.of(2026, 2, 28));
    ExecuteSweep sweep =
        sweepWith(
            opened -> {
              store.calls.add("file");
              throw new UncheckedIOException(
                  "SYNTHETIC DEVICE FULL", new java.io.IOException("no space left"));
            });

    assertThatThrownBy(() -> sweep.execute(RUN, BUSINESS_DATE))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage("SYNTHETIC DEVICE FULL");

    assertThat(store.completions).as("COMPLETED is never recorded").isEmpty();
    assertThat(store.calls)
        .as("the file is attempted after the case and its event, and nothing follows it")
        .containsExactly("open:1003000222", "event:RevalidationDue:1003000222", "file");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] the REVALQ file is written once per run: a failing writer is asked exactly once, with every case of the run")
  void theFileIsWrittenOnce() {
    store.track("1003000222", "A", LocalDate.of(2026, 2, 28));
    store.track("1003000333", "A", LocalDate.of(2026, 3, 31));
    List<List<String>> asked = new ArrayList<>();
    ExecuteSweep sweep =
        sweepWith(
            opened -> {
              asked.add(opened.stream().map(RevalidationCase::npi).toList());
              throw new IllegalStateException("SYNTHETIC");
            });

    assertThatThrownBy(() -> sweep.execute(RUN, BUSINESS_DATE))
        .isInstanceOf(IllegalStateException.class);

    assertThat(asked).containsExactly(List.of("1003000222", "1003000333"));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a sweep that does not fail records no failure itself: only the job reports a failed run")
  void aSuccessfulSweepRecordsNoFailure() {
    store.track("1003000222", "A", LocalDate.of(2026, 2, 28));

    sweepWith(opened -> new byte[0]).execute(RUN, BUSINESS_DATE);

    assertThat(store.failures).isEmpty();
    assertThat(store.completions).hasSize(1);
  }
}
