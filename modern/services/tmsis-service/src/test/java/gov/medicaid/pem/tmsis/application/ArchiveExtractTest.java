package gov.medicaid.pem.tmsis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.nio.charset.StandardCharsets;
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
 * MAM-75 AC-2, AC-4, AC-5 and AC-6: the last step of a run (PRVMTH01 STEP030) writes the run's
 * records in ascending NPI order as the TMSISOUT file, archives it as the next generation, completes
 * the run with its row count and puts ExtractProduced in the outbox, in that order and only when
 * every step before worked.
 */
class ArchiveExtractTest {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-03-31");
  static final String PERIOD = "26090";

  /** One event the outbox received. */
  record Event(String type, String subject, Object data) {}

  final FakeExtractRuns runs = new FakeExtractRuns();
  final List<Event> events = new ArrayList<>();
  final List<List<TmsisRecord>> written = new ArrayList<>();
  RuntimeException fileFails;
  RuntimeException outboxFails;

  /** The file writer of the test: one line "NPI;" per record, so the bytes show the order. */
  final TmsisFile file =
      records -> {
        runs.calls.add("write");
        if (fileFails != null) {
          throw fileFails;
        }
        written.add(records);
        StringBuilder text = new StringBuilder();
        records.forEach(r -> text.append(r.npi()).append(';'));
        return text.toString().getBytes(StandardCharsets.US_ASCII);
      };

  final EventOutbox outbox =
      (type, subject, data) -> {
        runs.calls.add("enqueue");
        if (outboxFails != null) {
          throw outboxFails;
        }
        events.add(new Event(type, subject, data));
      };

  final ArchiveExtract archive = new ArchiveExtract(runs, file, outbox, CLOCK);

  /** A RUNNING run for 2026-03-31 whose extract step stored these NPIs (in this order). */
  UUID runWithRows(String... npis) {
    UUID id = UUID.randomUUID();
    runs.create(
        new ExtractRun(
            id,
            BUSINESS_DATE,
            PERIOD,
            ExtractRun.RUNNING,
            0,
            0,
            null,
            null,
            Instant.parse("2026-01-15T09:00:00Z"),
            null));
    List<TmsisRecord> records = new ArrayList<>();
    for (String npi : npis) {
      records.add(new TmsisRecord("M00000" + npi.substring(7), npi, "A", PERIOD));
    }
    runs.addRows(id, records);
    runs.calls.clear();
    return id;
  }

  // ---- AC-4: the file and the archive generation ------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-4] the file is written from the run's rows in ascending NPI order, whatever order they were stored in")
  void fileInNpiOrder() {
    UUID id = runWithRows("1003000333", "1003000111", "1003000222");
    archive.archive(id);
    assertThat(written).hasSize(1);
    assertThat(written.get(0))
        .extracting(TmsisRecord::npi)
        .containsExactly("1003000111", "1003000222", "1003000333");
    assertThat(new String(runs.files.get(id), StandardCharsets.US_ASCII))
        .isEqualTo("1003000111;1003000222;1003000333;");
  }

  @Test
  @DisplayName("[MAM-75 AC-4] the archive generation of the file is the one the run answers with")
  void generationReturned() {
    assertThat(archive.archive(runWithRows("1003000111"))).isEqualTo(1);
    assertThat(archive.archive(runWithRows("1003000111"))).isEqualTo(2);
    assertThat(archive.archive(runWithRows("1003000111"))).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] every run is archived as a generation of its own: three runs keep three files")
  void everyRunKeepsItsFile() {
    UUID a = runWithRows("1003000111");
    UUID b = runWithRows("1003000111", "1003000333");
    archive.archive(a);
    archive.archive(b);
    assertThat(runs.files).containsOnlyKeys(a, b);
    assertThat(runs.files.get(a)).isNotEqualTo(runs.files.get(b));
    assertThat(runs.runs.get(a).archiveGeneration()).isEqualTo(1);
    assertThat(runs.runs.get(b).archiveGeneration()).isEqualTo(2);
  }

  // ---- AC-2: the result and the row count -------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-2] the run is COMPLETED with the number of records written as its row count and the clock's time as endedAt")
  void runCompletedWithRowCount() {
    UUID id = runWithRows("1003000111", "1003000333");
    archive.archive(id);
    ExtractRun run = runs.runs.get(id);
    assertThat(run.status()).isEqualTo(ExtractRun.COMPLETED);
    assertThat(run.rows()).isEqualTo(2);
    assertThat(run.endedAt()).isEqualTo(NOW);
    assertThat(run.error()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-2] a run with no rows is COMPLETED with 0 rows and an empty file, and is announced")
  void zeroRows() {
    UUID id = runWithRows();
    archive.archive(id);
    assertThat(runs.runs.get(id).status()).isEqualTo(ExtractRun.COMPLETED);
    assertThat(runs.runs.get(id).rows()).isZero();
    assertThat(runs.files.get(id)).isEmpty();
    assertThat(((ExtractProducedData) events.get(0).data()).rowCount()).isZero();
  }

  // ---- AC-5: ExtractProduced ---------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-75 AC-5] exactly one event is put in the outbox, of the type ExtractProduced")
  void oneExtractProduced() {
    archive.archive(runWithRows("1003000111", "1003000333"));
    assertThat(events).hasSize(1);
    assertThat(events.get(0).type()).isEqualTo("ExtractProduced");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event's subject, the Kafka key, is the run id (Q-061 B), not an NPI")
  void subjectIsTheRunId() {
    UUID id = runWithRows("1003000111");
    archive.archive(id);
    assertThat(events.get(0).subject()).isEqualTo(id.toString());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event data: runId, businessDate 2026-03-31, periodYyddd 26090 and rowCount 2")
  void eventData() {
    UUID id = runWithRows("1003000111", "1003000333");
    archive.archive(id);
    assertThat(events.get(0).data())
        .isEqualTo(new ExtractProducedData(id, BUSINESS_DATE, PERIOD, 2));
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event carries the run's business date and period, not the date of the clock (2026-01-15)")
  void eventUsesTheRunsDate() {
    archive.archive(runWithRows("1003000111"));
    ExtractProducedData data = (ExtractProducedData) events.get(0).data();
    assertThat(data.businessDate()).isEqualTo(BUSINESS_DATE).isNotEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(data.periodYyddd()).isEqualTo("26090").isNotEqualTo("26015");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event is put in the outbox only after the file is archived and the run completed")
  void eventAfterCompletion() {
    archive.archive(runWithRows("1003000111"));
    assertThat(runs.calls).containsExactly("find", "rows", "write", "complete", "enqueue");
  }

  // ---- AC-6: nothing is left behind or announced when a step fails ------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a run that does not exist is an error: nothing is written, archived or announced")
  void unknownRun() {
    UUID unknown = UUID.randomUUID();
    assertThatThrownBy(() -> archive.archive(unknown))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(unknown.toString());
    assertThat(written).isEmpty();
    assertThat(runs.files).isEmpty();
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] when the file cannot be written nothing is archived, the run is not completed and nothing is announced")
  void fileWriteFails() {
    UUID id = runWithRows("1003000111");
    fileFails = new IllegalStateException("record 1 does not fit");
    assertThatThrownBy(() -> archive.archive(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("record 1 does not fit");
    assertThat(runs.calls).doesNotContain("complete", "enqueue");
    assertThat(runs.files).isEmpty();
    assertThat(runs.runs.get(id).status()).isEqualTo(ExtractRun.RUNNING);
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] when the archive write fails the failure is not hidden and nothing is announced")
  void archiveWriteFails() {
    UUID id = runWithRows("1003000111");
    runs.completeFails = new IllegalStateException("the archive is full");
    assertThatThrownBy(() -> archive.archive(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the archive is full");
    assertThat(runs.calls).doesNotContain("enqueue");
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] when the event cannot be put in the outbox the failure is not hidden (the transaction then rolls the archive back)")
  void outboxWriteFails() {
    UUID id = runWithRows("1003000111");
    outboxFails = new IllegalStateException("the outbox rejected the event");
    assertThatThrownBy(() -> archive.archive(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the outbox rejected the event");
    assertThat(events).isEmpty();
  }
}
