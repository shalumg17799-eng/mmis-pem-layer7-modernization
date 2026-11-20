package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.application.Screening;
import gov.medicaid.pem.screening.application.ScreeningRuns;
import gov.medicaid.pem.screening.application.ScreeningStore;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MAM-32 AC-5 and AC-6 on the run table (screening_run and the run link of screening.run_id): what
 * a run records, that a COMPLETED run is never turned into a FAILED one, and what the file and the
 * counts are made of.
 */
class JdbcScreeningRunsTest extends Lane3Test {

  static final LocalDate DATE = LocalDate.of(2026, 3, 31);
  static final Instant START = Instant.parse("2026-03-31T08:00:00.123456Z");
  static final Instant END = Instant.parse("2026-03-31T08:00:05.654321Z");

  @Autowired ScreeningRuns runs;
  @Autowired ScreeningStore store;

  UUID newRun() {
    UUID id = UUID.randomUUID();
    runs.create(id, DATE, START);
    return id;
  }

  Screening screening(String npi, String reason, String result) {
    Instant at = Instant.parse("2026-03-31T08:00:01.000001Z");
    return new Screening(
        UUID.randomUUID(),
        npi,
        reason,
        "COMPLETED",
        result,
        List.of(
            new SourceResult(Source.LICBOARD, "P", at, "LICENCE OK", null),
            new SourceResult(Source.OIGLEIE, "P", at, "NOT EXCLUDED", null),
            new SourceResult(Source.SAMGOV, "P", at, "NOT DEBARRED", null),
            new SourceResult(Source.SSADMF, "P", at, "NOT DECEASED", null)),
        at,
        at);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-1] a created run is RUNNING with the business date and the start time, no counts, no error, no end")
  void created() {
    UUID id = newRun();
    ScreeningRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.runId()).isEqualTo(id);
    assertThat(run.businessDate()).isEqualTo(DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.startedAt()).isEqualTo(START);
    assertThat(run.endedAt()).isNull();
    assertThat(run.error()).isNull();
    assertThat(run.read()).isZero();
    assertThat(run.screened()).isZero();
    assertThat(run.failed()).isZero();
    assertThat(run.skipped()).isZero();
  }

  @Test
  @DisplayName("[MAM-32 AC-5] a run that is not there is not found, and has no file")
  void unknown() {
    assertThat(runs.find(UUID.randomUUID())).isEmpty();
    assertThat(runs.file(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-5] a completed run keeps the read and failed counts, the end time and the file")
  void completed() {
    UUID id = newRun();
    byte[] file = "1003000111LICBOARDP20260331X".getBytes(StandardCharsets.US_ASCII);
    runs.complete(id, 4, 1, file, END);
    ScreeningRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.read()).isEqualTo(4);
    assertThat(run.failed()).isEqualTo(1);
    assertThat(run.endedAt()).isEqualTo(END);
    assertThat(run.error()).isNull();
    assertThat(runs.file(id)).isPresent();
    assertThat(runs.file(id).orElseThrow()).isEqualTo(file);
  }

  @Test
  @DisplayName("[MAM-32 AC-7] an empty file of a completed run is a file, not a missing file")
  void emptyFileIsAFile() {
    UUID id = newRun();
    runs.complete(id, 4, 0, new byte[0], END);
    assertThat(runs.file(id)).isPresent();
    assertThat(runs.file(id).orElseThrow()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a failed run keeps the reason, the read count and the end time, and has no file")
  void failed() {
    UUID id = newRun();
    runs.fail(id, 200, "provider-service could not be read", END);
    ScreeningRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("provider-service could not be read");
    assertThat(run.read()).isEqualTo(200);
    assertThat(run.endedAt()).isEqualTo(END);
    assertThat(runs.file(id)).isEmpty();
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a COMPLETED run is never turned into FAILED afterwards")
  void completedStaysCompleted() {
    UUID id = newRun();
    runs.complete(id, 4, 0, "X".getBytes(StandardCharsets.US_ASCII), END);
    runs.fail(id, 0, "late failure", END.plusSeconds(5));
    ScreeningRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.error()).isNull();
    assertThat(run.endedAt()).isEqualTo(END);
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a failure without a reason is recorded with a reason, never blank")
  void blankReason() {
    UUID a = newRun();
    runs.fail(a, 0, null, END);
    UUID b = newRun();
    runs.fail(b, 0, "   ", END);
    assertThat(runs.find(a).orElseThrow().error()).isNotBlank();
    assertThat(runs.find(b).orElseThrow().error()).isNotBlank();
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a reason longer than the column is cut, not refused")
  void longReasonIsCut() {
    UUID id = newRun();
    runs.fail(id, 0, "R".repeat(2000), END);
    ScreeningRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isNotBlank().hasSizeLessThanOrEqualTo(500).startsWith("RRRR");
  }

  @Test
  @DisplayName("[MAM-32 AC-7] only an INITIAL screening of the NPI counts as screened before")
  void hasInitialScreening() {
    store.save(screening("1003000111", "INITIAL", "PASS"));
    store.save(screening("1003000222", "MANUAL", "PASS"));
    store.save(screening("1003000333", "REVALIDATION", "PASS"));
    assertThat(runs.hasInitialScreening("1003000111")).isTrue();
    assertThat(runs.hasInitialScreening("1003000222")).isFalse();
    assertThat(runs.hasInitialScreening("1003000333")).isFalse();
    assertThat(runs.hasInitialScreening("1003000444")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-5] screened is the number of screenings linked to the run, skipped is read minus screened")
  void linkAndCounts() {
    UUID run = newRun();
    UUID other = newRun();
    Screening a = screening("1003000111", "INITIAL", "PASS");
    Screening b = screening("1003000555", "INITIAL", "FAIL");
    Screening c = screening("1003000666", "INITIAL", "PASS");
    store.save(a);
    store.save(b);
    store.save(c);
    runs.link(a.screeningId(), run);
    runs.link(b.screeningId(), run);
    runs.link(c.screeningId(), other);
    runs.complete(run, 4, 1, new byte[0], END);
    ScreeningRuns.Run r = runs.find(run).orElseThrow();
    assertThat(r.screened()).isEqualTo(2);
    assertThat(r.skipped()).isEqualTo(2);
    assertThat(runs.find(other).orElseThrow().screened()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] the screened providers of a run come in ascending NPI order with their four results in the stored order")
  void screenedInNpiOrder() {
    UUID run = newRun();
    for (String npi : List.of("1003000555", "1003000111", "1003000333")) {
      Screening s = screening(npi, "INITIAL", "PASS");
      store.save(s);
      runs.link(s.screeningId(), run);
    }
    Screening elsewhere = screening("1003000001", "INITIAL", "PASS");
    store.save(elsewhere);
    List<ScreeningRuns.Screened> screened = runs.screened(run);
    assertThat(screened.stream().map(ScreeningRuns.Screened::npi))
        .containsExactly("1003000111", "1003000333", "1003000555");
    assertThat(screened.get(0).results().stream().map(r -> r.source()))
        .containsExactly(Source.LICBOARD, Source.OIGLEIE, Source.SAMGOV, Source.SSADMF);
    assertThat(screened.get(0).results().get(0).detail()).isEqualTo("LICENCE OK");
  }

  @Test
  @DisplayName("[MAM-32 AC-3] a run without screenings has no screened providers")
  void noneScreened() {
    assertThat(runs.screened(newRun())).isEmpty();
  }
}
