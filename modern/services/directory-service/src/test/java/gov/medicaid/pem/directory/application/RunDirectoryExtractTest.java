package gov.medicaid.pem.directory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-67: the work of one directory extract run (PRVDIR01), with in-memory ports: the run is
 * recorded RUNNING for an explicit business date, every published entry reaches the file writer in
 * the order the store gives it (ascending NPI), the run is completed with the row count and the
 * file, and a failure is recorded with its reason.
 */
class RunDirectoryExtractTest {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-03-31");

  /** The published entries in NPI order, as directory_db would give them. */
  final List<DirectoryEntry> published = new ArrayList<>();

  final List<String> storeCalls = new ArrayList<>();
  final Map<UUID, DirectoryExtracts.Run> runs = new HashMap<>();
  final Map<UUID, byte[]> files = new HashMap<>();
  final List<String> runCalls = new ArrayList<>();
  final List<List<DirectoryEntry>> written = new ArrayList<>();
  RuntimeException writerFailure;

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
          storeCalls.add(name + "/" + providerType + "/" + taxonomy + "/" + page + "/" + size);
          int from = Math.min(page * size, published.size());
          int to = Math.min(from + size, published.size());
          return new DirectoryPage(published.subList(from, to), page, size, published.size());
        }
      };

  final DirectoryExtracts extracts =
      new DirectoryExtracts() {
        @Override
        public void create(Run run) {
          runCalls.add("create");
          runs.put(run.runId(), run);
        }

        @Override
        public void complete(UUID runId, int rows, byte[] file, Instant endedAt) {
          runCalls.add("complete");
          Run r = runs.get(runId);
          runs.put(
              runId,
              new Run(runId, r.businessDate(), Run.COMPLETED, rows, null, r.startedAt(), endedAt));
          files.put(runId, file);
        }

        @Override
        public void fail(UUID runId, String error, Instant endedAt) {
          runCalls.add("fail");
          Run r = runs.get(runId);
          runs.put(
              runId,
              new Run(
                  runId, r.businessDate(), Run.FAILED, r.rows(), error, r.startedAt(), endedAt));
        }

        @Override
        public Optional<Run> find(UUID runId) {
          return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public Optional<byte[]> file(UUID runId) {
          return Optional.ofNullable(files.get(runId));
        }
      };

  final DirectoryFile writer =
      entries -> {
        if (writerFailure != null) {
          throw writerFailure;
        }
        written.add(List.copyOf(entries));
        return ("FILE OF " + entries.size()).getBytes(StandardCharsets.US_ASCII);
      };

  final RunDirectoryExtract extract = new RunDirectoryExtract(extracts, store, writer, CLOCK);

  static DirectoryEntry entry(int n) {
    String npi = String.format("10030%05d", n);
    return new DirectoryEntry(npi, "M" + npi.substring(2), "IN", "L", "A", "PROVIDER " + n, null);
  }

  void publish(int count) {
    for (int i = 1; i <= count; i++) {
      published.add(entry(i));
    }
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] a run is recorded RUNNING for the business date, with the start time and no end")
  void open() {
    DirectoryExtracts.Run run = extract.open(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.rows()).isZero();
    assertThat(run.error()).isNull();
    assertThat(run.startedAt()).isEqualTo(NOW);
    assertThat(run.endedAt()).isNull();
    assertThat(run.runId()).isNotNull();
    assertThat(runs).containsEntry(run.runId(), run);
  }

  @Test
  @DisplayName("[MAM-67 AC-6] every call opens a new run with its own id, also for the same date")
  void openTwice() {
    UUID first = extract.open(BUSINESS_DATE).runId();
    UUID second = extract.open(BUSINESS_DATE).runId();
    assertThat(first).isNotEqualTo(second);
    assertThat(runs).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-3] every published entry goes to the file writer, in the order the store gives")
  void entriesInOrder() {
    publish(3);
    UUID id = extract.open(BUSINESS_DATE).runId();
    extract.execute(id, BUSINESS_DATE);
    assertThat(written).containsExactly(published);
  }

  @Test
  @DisplayName("[MAM-67 AC-1] the run is completed with the row count, the file and the end time")
  void completed() {
    publish(2);
    UUID id = extract.open(BUSINESS_DATE).runId();
    int rows = extract.execute(id, BUSINESS_DATE);
    assertThat(rows).isEqualTo(2);
    DirectoryExtracts.Run run = runs.get(id);
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.rows()).isEqualTo(2);
    assertThat(run.endedAt()).isEqualTo(NOW);
    assertThat(run.error()).isNull();
    assertThat(new String(files.get(id), StandardCharsets.US_ASCII)).isEqualTo("FILE OF 2");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-3] with no published entry the run is completed with 0 rows and an empty list is written")
  void nothingPublished() {
    UUID id = extract.open(BUSINESS_DATE).runId();
    int rows = extract.execute(id, BUSINESS_DATE);
    assertThat(rows).isZero();
    assertThat(written).containsExactly(List.of());
    assertThat(runs.get(id).status()).isEqualTo("COMPLETED");
    assertThat(runs.get(id).rows()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-3] the store is asked for published entries without any filter, 200 a page from page 0")
  void storeAskedForEverything() {
    UUID id = extract.open(BUSINESS_DATE).runId();
    extract.execute(id, BUSINESS_DATE);
    assertThat(storeCalls).containsExactly("null/null/null/0/200");
  }

  @ParameterizedTest(name = "[MAM-67 AC-3] {0} published entries are all written, in order")
  @ValueSource(ints = {1, 199, 200, 201, 399, 400, 401, 450})
  void paging(int count) {
    publish(count);
    UUID id = extract.open(BUSINESS_DATE).runId();
    int rows = extract.execute(id, BUSINESS_DATE);
    assertThat(rows).isEqualTo(count);
    assertThat(written).hasSize(1);
    assertThat(written.get(0)).containsExactlyElementsOf(published);
    assertThat(runs.get(id).rows()).isEqualTo(count);
  }

  @ParameterizedTest(
      name = "[MAM-67 AC-3] {0} published entries need {1} store calls, none past the last page")
  @CsvSource({"0,1", "1,1", "199,1", "200,1", "201,2", "399,2", "400,2", "401,3"})
  void pagesAskedFor(int count, int calls) {
    publish(count);
    extract.execute(extract.open(BUSINESS_DATE).runId(), BUSINESS_DATE);
    assertThat(storeCalls).hasSize(calls);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] when the file cannot be written the run is not completed and the failure reaches the job")
  void writerFails() {
    publish(1);
    writerFailure = new IllegalStateException("disk full");
    UUID id = extract.open(BUSINESS_DATE).runId();
    assertThatThrownBy(() -> extract.execute(id, BUSINESS_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("disk full");
    assertThat(runCalls).containsExactly("create");
    assertThat(runs.get(id).status()).isEqualTo("RUNNING");
    assertThat(files).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] a failed run is recorded FAILED with the reason and the end time, and has no file")
  void failed() {
    UUID id = extract.open(BUSINESS_DATE).runId();
    extract.failed(id, BUSINESS_DATE, "the database rejected the write");
    DirectoryExtracts.Run run = runs.get(id);
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("the database rejected the write");
    assertThat(run.endedAt()).isEqualTo(NOW);
    assertThat(extracts.file(id)).isEmpty();
  }
}
