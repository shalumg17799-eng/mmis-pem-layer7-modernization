package gov.medicaid.pem.screening.application;

import static gov.medicaid.pem.screening.application.Fakes.CLOCK;
import static gov.medicaid.pem.screening.application.Fakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.application.Fakes.Outbox;
import gov.medicaid.pem.screening.application.Fakes.RunsFake;
import gov.medicaid.pem.screening.application.Fakes.Store;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-32: the steps of the batch screening (PRVSCR01) without Spring: which providers are screened
 * (R-PRVSCR01-cbl-00022: screen flag U, AC-7: no INITIAL screening yet), the failed count, the
 * file and the run status (D-024).
 */
class RunScreeningBatchTest {

  static final LocalDate DATE = LocalDate.of(2026, 3, 31);

  final RunsFake runs = new RunsFake();
  final Store store = new Store();
  final Outbox outbox = new Outbox();
  final List<List<ScreeningRuns.Screened>> filesFor = new ArrayList<>();
  final List<LocalDate> fileDates = new ArrayList<>();
  List<SourceResult> answers = Fakes.results("P", "P", "P", "P");

  final ScreenProvider screen =
      new ScreenProvider(Fakes::facts, facts -> answers, store, outbox, CLOCK);

  final ScrnoutFile file =
      (screened, date) -> {
        filesFor.add(screened);
        fileDates.add(date);
        return "FILE".getBytes(StandardCharsets.US_ASCII);
      };

  final RunScreeningBatch batch = new RunScreeningBatch(runs, screen, file, CLOCK);

  @Test
  @DisplayName("[MAM-32 AC-1] a run is opened RUNNING for the explicit business date")
  void open() {
    ScreeningRuns.Run run = batch.open(DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.businessDate()).isEqualTo(DATE);
    assertThat(run.startedAt()).isEqualTo(NOW);
    assertThat(run.endedAt()).isNull();
    assertThat(runs.runs).containsKey(run.runId());
  }

  @Test
  @DisplayName("[MAM-32 AC-2] two opened runs have two different ids")
  void openTwice() {
    assertThat(batch.open(DATE).runId()).isNotEqualTo(batch.open(DATE).runId());
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-2] a provider with screen flag U and no INITIAL screening is screened with reason INITIAL and linked to the run")
  void unscreenedIsScreened() {
    UUID run = batch.open(DATE).runId();
    boolean screened = batch.screenIfDue(run, new ProviderListing.Row("1003000111", "U"));
    assertThat(screened).isTrue();
    assertThat(store.saved).hasSize(1);
    Screening s = store.saved.get(0);
    assertThat(s.npi()).isEqualTo("1003000111");
    assertThat(s.reason()).isEqualTo("INITIAL");
    assertThat(runs.links).containsExactly(java.util.Map.entry(s.screeningId(), run));
    assertThat(outbox.sent).hasSize(1);
    assertThat(outbox.sent.get(0).type()).isEqualTo("ScreeningCompleted");
  }

  @ParameterizedTest(name = "[MAM-32 AC-2] a provider with screen flag <{0}> is skipped")
  @NullSource
  @ValueSource(strings = {"P", "F", "u", "", " ", "UU", " U", "X"})
  void otherFlagsAreSkipped(String flag) {
    boolean screened =
        batch.screenIfDue(batch.open(DATE).runId(), new ProviderListing.Row("1003000222", flag));
    assertThat(screened).isFalse();
    assertThat(store.saved).isEmpty();
    assertThat(runs.links).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] a provider that is still U but has an INITIAL screening is not screened again")
  void alreadyScreenedIsSkipped() {
    runs.initial.add("1003000111");
    boolean screened =
        batch.screenIfDue(batch.open(DATE).runId(), new ProviderListing.Row("1003000111", "U"));
    assertThat(screened).isFalse();
    assertThat(store.saved).isEmpty();
    assertThat(runs.links).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-32 AC-7] an INITIAL screening of another provider does not stop this one")
  void otherProvidersInitialDoesNotCount() {
    runs.initial.add("1003000555");
    assertThat(
            batch.screenIfDue(batch.open(DATE).runId(), new ProviderListing.Row("1003000111", "U")))
        .isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-5] failed counts the screened providers with a result other than P: F or C")
  void failedCount() {
    UUID run = batch.open(DATE).runId();
    runs.screenedOfRun =
        List.of(
            new ScreeningRuns.Screened("1003000111", Fakes.results("P", "P", "P", "P")),
            new ScreeningRuns.Screened("1003000222", Fakes.results("F", "P", "P", "P")),
            new ScreeningRuns.Screened("1003000333", Fakes.results("P", "P", "C", "P")),
            new ScreeningRuns.Screened("1003000444", Fakes.results("P", "P", "P", "P")));
    batch.finish(run, DATE, 9);
    ScreeningRuns.Run done = runs.runs.get(run);
    assertThat(done.status()).isEqualTo("COMPLETED");
    assertThat(done.read()).isEqualTo(9);
    assertThat(done.failed()).isEqualTo(2);
    assertThat(done.screened()).isEqualTo(4);
    assertThat(done.skipped()).isEqualTo(5);
    assertThat(done.endedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("[MAM-32 AC-5] a run that screened nobody is COMPLETED with zero failed")
  void nobodyScreened() {
    UUID run = batch.open(DATE).runId();
    batch.finish(run, DATE, 4);
    ScreeningRuns.Run done = runs.runs.get(run);
    assertThat(done.status()).isEqualTo("COMPLETED");
    assertThat(done.failed()).isZero();
    assertThat(done.skipped()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] the file is written from the run's screened providers with the business date as verdict date")
  void fileWritten() {
    UUID run = batch.open(DATE).runId();
    runs.screenedOfRun =
        List.of(new ScreeningRuns.Screened("1003000111", Fakes.results("P", "P", "P", "P")));
    batch.finish(run, DATE, 1);
    assertThat(filesFor).containsExactly(runs.screenedOfRun);
    assertThat(fileDates).containsExactly(DATE);
    assertThat(new String(runs.fileWritten, StandardCharsets.US_ASCII)).isEqualTo("FILE");
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a failed run is recorded FAILED with the reason, the read count and the end time")
  void failed() {
    UUID run = batch.open(DATE).runId();
    batch.failed(run, DATE, 200, "provider-service could not be read");
    ScreeningRuns.Run done = runs.runs.get(run);
    assertThat(done.status()).isEqualTo("FAILED");
    assertThat(done.error()).isEqualTo("provider-service could not be read");
    assertThat(done.read()).isEqualTo(200);
    assertThat(done.endedAt()).isEqualTo(NOW);
    assertThat(runs.fileWritten).isNull();
  }
}
