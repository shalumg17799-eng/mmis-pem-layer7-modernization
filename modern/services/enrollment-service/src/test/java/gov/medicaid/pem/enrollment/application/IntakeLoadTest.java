package gov.medicaid.pem.enrollment.application;

import static gov.medicaid.pem.enrollment.application.IntakeFakes.DATE;
import static gov.medicaid.pem.enrollment.application.IntakeFakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.application.IntakeFakes.Intake;
import gov.medicaid.pem.enrollment.domain.ValidationError;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * The APPINPUT intake use case (MAM-56, MAM-57; PRVENR01) without Spring or a database: the run
 * table, the application table and the outbox are in-memory fakes, the clock is fixed. What is
 * stored, counted, written to the error file and announced is decided here.
 */
class IntakeLoadTest {

  static final String NPI_1 = "1003000111";
  static final String NPI_2 = "1003000222";
  static final String NPI_3 = "1003000333";
  static final String NPI_4 = "1003000777";

  final Intake in = new Intake();
  final Logger logger = (Logger) LoggerFactory.getLogger(IntakeLoad.class);
  final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void listen() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopListening() {
    logger.detachAppender(appender);
    appender.stop();
  }

  List<String> logged(Level level) {
    return appender.list.stream()
        .filter(e -> e.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  static List<String> codes(List<ValidationError> errors) {
    return errors.stream().map(e -> e.code().name() + "/" + e.field()).toList();
  }

  // ---- open: batch id and business date (D-017, D-020) ---------------------------------------

  @Test
  @DisplayName(
      "[MAM-56 AC-1] open creates the run with its batch id, business date and start time and returns the run id")
  void openCreatesTheRun() {
    UUID id = in.load.open(DATE, "B0331001");
    assertThat(id).isNotNull();
    assertThat(in.runs.createdRun).isEqualTo(id);
    assertThat(in.runs.createdBatch).isEqualTo("B0331001");
    assertThat(in.runs.createdDate).isEqualTo(DATE);
    assertThat(in.runs.createdAt).isEqualTo(NOW);
    assertThat(in.runs.calls).containsExactly("create");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-1] open logs the legacy line 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH <id>'")
  void openLogsTheLegacyLine() {
    in.load.open(DATE, "B0331001");
    assertThat(logged(Level.INFO)).contains("PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001");
  }

  @ParameterizedTest(name = "[MAM-56 AC-2] batch id [{0}] becomes DEFBATCH")
  @NullSource
  @ValueSource(strings = {"", " ", "        ", "\t"})
  void blankBatchIdIsDefbatch(String batchId) {
    in.load.open(DATE, batchId);
    assertThat(in.runs.createdBatch).isEqualTo("DEFBATCH");
    assertThat(logged(Level.INFO)).contains("PRVENR01: STARTING ENROLLMENT LOAD - BATCH DEFBATCH");
  }

  @Test
  @DisplayName("[MAM-56 AC-1] a batch id with blanks around it is kept without them")
  void batchIdIsTrimmed() {
    in.load.open(DATE, "  B0331001 ");
    assertThat(in.runs.createdBatch).isEqualTo("B0331001");
  }

  @Test
  @DisplayName("[MAM-56 AC-1] every run gets its own id")
  void runIdsAreUnique() {
    assertThat(in.load.open(DATE, "B1")).isNotEqualTo(in.load.open(DATE, "B1"));
  }

  // ---- records: how the file is cut into records -----------------------------------------------

  static List<String> texts(byte[] file) {
    return IntakeLoad.records(file).stream()
        .map(r -> new String(r, StandardCharsets.ISO_8859_1))
        .toList();
  }

  static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a file with line ends has one record per line (LF)")
  void recordsPerLine() {
    assertThat(texts(bytes("AAA\nBBB\nCCC\n"))).containsExactly("AAA", "BBB", "CCC");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a CR before the LF is not part of the record (CR LF files)")
  void crlfLines() {
    assertThat(texts(bytes("AAA\r\nBBB\r\n"))).containsExactly("AAA", "BBB");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] the last line needs no line end")
  void lastLineWithoutLineEnd() {
    assertThat(texts(bytes("AAA\nBBB"))).containsExactly("AAA", "BBB");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a final line end does not add an empty record")
  void finalLineEndAddsNothing() {
    assertThat(texts(bytes("AAA\n"))).containsExactly("AAA");
    assertThat(texts(bytes("AAA\r\n"))).containsExactly("AAA");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] an empty line in the middle is a record of zero bytes, so it is unreadable")
  void emptyLineIsARecord() {
    assertThat(texts(bytes("AAA\n\nBBB\n"))).containsExactly("AAA", "", "BBB");
    assertThat(texts(bytes("AAA\r\n\r\nBBB\n"))).containsExactly("AAA", "", "BBB");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a CR that is not before an LF belongs to the record")
  void loneCrStays() {
    assertThat(texts(bytes("AA\rBB\nCC"))).containsExactly("AA\rBB", "CC");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] a file with no line end is read in blocks of 200 bytes (fixed-block file)")
  void blocksOf200() {
    byte[] file = AppInputFile.blocks(AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2));
    assertThat(file).hasSize(400);
    assertThat(texts(file)).containsExactly(AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2));
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] the last block of a fixed-block file may be short: it is a short record")
  void shortLastBlock() {
    byte[] file = bytes("X".repeat(200) + "Y".repeat(50));
    List<String> records = texts(file);
    assertThat(records).hasSize(2);
    assertThat(records.get(0)).hasSize(200);
    assertThat(records.get(1)).isEqualTo("Y".repeat(50));
  }

  @Test
  @DisplayName("[MAM-56 AC-3] exactly one block of 200 bytes is one record")
  void oneBlock() {
    assertThat(texts(bytes("Z".repeat(200)))).containsExactly("Z".repeat(200));
  }

  @Test
  @DisplayName("[MAM-57 AC-1] an empty file has no records")
  void emptyFile() {
    assertThat(IntakeLoad.records(new byte[0])).isEmpty();
  }

  // ---- process: a clean run ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-1] a clean run writes every record, counts after each one and completes at the clock time")
  void cleanRun() {
    in.process(AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2), AppInputFile.valid(NPI_3));
    assertThat(in.runs.counts)
        .containsExactly(new int[] {1, 1, 0, 0}, new int[] {2, 2, 0, 0}, new int[] {3, 3, 0, 0});
    assertThat(in.runs.completedAt).isEqualTo(NOW);
    assertThat(in.runs.failedReason).isNull();
    assertThat(in.runs.errorRows).isEmpty();
    assertThat(in.store.saved).hasSize(3);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-1] every stored record has source LEGACY_FILE and the name of whoever started the run")
  void sourceAndSubmitter() {
    in.process(AppInputFile.valid(NPI_1), AppInputFile.noTaxonomy(NPI_2));
    assertThat(in.store.saved).extracting(StoredApplication::source).containsOnly("LEGACY_FILE");
    assertThat(in.store.submittedBy).containsExactly("admin1", "admin1");
    assertThat(in.store.saved).extracting(StoredApplication::submittedAt).containsOnly(NOW);
  }

  @Test
  @DisplayName("[MAM-56 AC-5] the records are processed in file order")
  void fileOrder() {
    in.process(AppInputFile.valid(NPI_3), AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2));
    assertThat(in.store.saved)
        .extracting(StoredApplication::npi)
        .containsExactly(NPI_3, NPI_1, NPI_2);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-1] the run ends with a log line that gives the four counts of the legacy TERMINATE")
  void endLine() {
    in.process(
        AppInputFile.valid(NPI_1), AppInputFile.noTaxonomy(NPI_2), AppInputFile.valid(NPI_1));
    assertThat(logged(Level.INFO))
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("PRVENR01")
                    .contains("READ=3")
                    .contains("WRITTEN=1")
                    .contains("ERRORED=1")
                    .contains("SKIPPED=1"));
  }

  @Test
  @DisplayName("[MAM-57 AC-1] an empty file completes with nothing read and nothing stored")
  void emptyRun() {
    in.load.process(UUID.randomUUID(), DATE, new byte[0], "admin1");
    assertThat(in.runs.completedAt).isEqualTo(NOW);
    assertThat(in.runs.counts).isEmpty();
    assertThat(in.store.saved).isEmpty();
  }

  // ---- process: validation (AC-4, AC-5)
  // -----------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-4] a blank NPI is REJECTED with exactly E101 on npi and no event")
  void blankNpiRejected() {
    in.process(AppInputFile.noNpi());
    StoredApplication a = in.store.saved.get(0);
    assertThat(a.status()).isEqualTo("REJECTED");
    assertThat(codes(a.errors())).containsExactly("E101/npi");
    assertThat(in.outbox.sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-56 AC-4] type XX is REJECTED with exactly E102 on providerType")
  void unknownTypeRejected() {
    in.process(AppInputFile.ofType(NPI_2, "XX"));
    assertThat(codes(in.store.of(NPI_2).errors())).containsExactly("E102/providerType");
  }

  @Test
  @DisplayName("[MAM-56 AC-4] an empty taxonomy is REJECTED with exactly E104 on taxonomy")
  void emptyTaxonomyRejected() {
    in.process(AppInputFile.noTaxonomy(NPI_3));
    assertThat(codes(in.store.of(NPI_3).errors())).containsExactly("E104/taxonomy");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] a valid application is VALIDATED with no error and one ApplicationValidated event")
  void validApplication() {
    in.process(AppInputFile.valid(NPI_1));
    StoredApplication a = in.store.of(NPI_1);
    assertThat(a.status()).isEqualTo("VALIDATED");
    assertThat(a.errors()).isEmpty();
    assertThat(in.outbox.count("ApplicationValidated")).isEqualTo(1);
    assertThat(in.outbox.sent.get(0).subject()).isEqualTo(NPI_1);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] ApplicationRejected is announced for a rejected record with an NPI, not for one without")
  void rejectedEvents() {
    in.process(
        AppInputFile.noNpi(), AppInputFile.ofType(NPI_2, "XX"), AppInputFile.noTaxonomy(NPI_3));
    assertThat(in.outbox.sent)
        .extracting(IntakeFakes.Sent::type, IntakeFakes.Sent::subject)
        .containsExactly(tuple("ApplicationRejected", NPI_2), tuple("ApplicationRejected", NPI_3));
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-5] a rejected record does not stop the load: the records after it are processed")
  void rejectionDoesNotStopTheLoad() {
    in.process(
        AppInputFile.valid(NPI_1), AppInputFile.noTaxonomy(NPI_2), AppInputFile.valid(NPI_3));
    assertThat(in.store.saved)
        .extracting(StoredApplication::npi, StoredApplication::status)
        .containsExactly(
            tuple(NPI_1, "VALIDATED"), tuple(NPI_2, "REJECTED"), tuple(NPI_3, "VALIDATED"));
    assertThat(in.runs.lastCounts()).containsExactly(3, 2, 1, 0);
    assertThat(in.outbox.count("ApplicationValidated")).isEqualTo(2);
  }

  // ---- process: the APPERROR file (AC-6)
  // -----------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-56 AC-6] one APPERROR row per rejected record, none for valid ones, in input order")
  void errorRows() {
    in.process(
        AppInputFile.valid(NPI_1),
        AppInputFile.noNpi(),
        AppInputFile.individual(NPI_2, "M00000222", "XX", "", "RIVERA", "MARIA", "MD12345", "TX"),
        AppInputFile.noTaxonomy(NPI_3));
    assertThat(in.runs.errorRows)
        .containsExactly(
            AppInputFile.errorRow("", "IN"),
            AppInputFile.errorRow(NPI_2, "XX"),
            AppInputFile.errorRow(NPI_3, "IN"));
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-6] the row for 1003000222 type XX starts '1003000222 TYPE:XX ERR:VALIDATION-FAILED'")
  void errorRowStart() {
    in.process(AppInputFile.ofType(NPI_2, "XX"));
    assertThat(in.runs.errorRows.get(0)).startsWith("1003000222 TYPE:XX ERR:VALIDATION-FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-6] an APPERROR row is 250 characters: NPI 1-10, ' TYPE:' 11-16, type 17-18, ' ERR:VALIDATION-FAILED' 19-40, spaces 41-250")
  void errorRowLayout() {
    String row = IntakeLoad.errorRow(AppInputFile.ofType(NPI_2, "XX"));
    assertThat(row).hasSize(250);
    assertThat(row.substring(0, 10)).isEqualTo(NPI_2);
    assertThat(row.substring(10, 16)).isEqualTo(" TYPE:");
    assertThat(row.substring(16, 18)).isEqualTo("XX");
    assertThat(row.substring(18, 40)).isEqualTo(" ERR:VALIDATION-FAILED");
    assertThat(row.substring(40)).isEqualTo(" ".repeat(210));
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-6] the NPI and the type of a row are the bytes as read, a blank NPI is ten spaces")
  void errorRowAsRead() {
    assertThat(IntakeLoad.errorRow(AppInputFile.noNpi()))
        .startsWith("           TYPE:IN ERR:VALIDATION-FAILED")
        .hasSize(250);
    String odd =
        AppInputFile.individual(
            "ABCDEFGHIJ", "M00000999", "xx", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
    assertThat(IntakeLoad.errorRow(odd)).startsWith("ABCDEFGHIJ TYPE:xx ERR:VALIDATION-FAILED");
  }

  // ---- process: counts (MAM-57)
  // --------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-2] a record with two failed checks is counted once, as errored, and has one row")
  void twoFailedChecksCountOnce() {
    in.process(
        AppInputFile.valid(NPI_1),
        AppInputFile.valid(NPI_4),
        AppInputFile.noNpi(),
        AppInputFile.individual(NPI_2, "M00000222", "XX", "", "RIVERA", "MARIA", "MD12345", "TX"));
    int[] counts = in.runs.lastCounts();
    assertThat(counts).containsExactly(4, 2, 2, 0);
    assertThat(counts[0]).isEqualTo(counts[1] + counts[2] + counts[3]);
    assertThat(in.runs.errorRows).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] a duplicate NPI (E103 only) is skipped, REJECTED with one error and one APPERROR row, and not announced twice")
  void duplicateIsSkipped() {
    in.process(AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2));
    assertThat(in.runs.lastCounts()).containsExactly(3, 2, 0, 1);
    StoredApplication second = in.store.saved.get(1);
    assertThat(second.status()).isEqualTo("REJECTED");
    assertThat(codes(second.errors())).containsExactly("E103/npi");
    assertThat(in.runs.errorRows).containsExactly(AppInputFile.errorRow(NPI_1, "IN"));
    assertThat(in.outbox.sent)
        .filteredOn(s -> s.type().equals("ApplicationValidated") && s.subject().equals(NPI_1))
        .hasSize(1);
    assertThat(in.store.saved.get(0).status()).isEqualTo("VALIDATED");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] a duplicate NPI that also fails another check is errored, not skipped")
  void duplicateWithAnotherErrorIsErrored() {
    in.process(
        AppInputFile.valid(NPI_1),
        AppInputFile.individual(
            NPI_1, "M00000111", "ZZ", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX"));
    assertThat(codes(in.store.saved.get(1).errors()))
        .containsExactlyInAnyOrder("E103/npi", "E102/providerType");
    assertThat(in.runs.lastCounts()).containsExactly(2, 1, 1, 0);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] the counts are written after every record, so a failed run shows its progress")
  void countsAfterEveryRecord() {
    in.process(
        AppInputFile.valid(NPI_1), AppInputFile.noTaxonomy(NPI_2), AppInputFile.valid(NPI_1));
    assertThat(in.runs.counts)
        .containsExactly(new int[] {1, 1, 0, 0}, new int[] {2, 1, 1, 0}, new int[] {3, 1, 1, 1});
  }

  @Test
  @DisplayName("[MAM-57 AC-4] a run with rejected applications completes, it is not failed")
  void rejectionsCompleteTheRun() {
    in.process(
        AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2), AppInputFile.noTaxonomy(NPI_3));
    assertThat(in.runs.calls).endsWith("complete");
    assertThat(in.runs.failedReason).isNull();
    assertThat(in.runs.completedAt).isEqualTo(NOW);
    assertThat(in.store.of(NPI_1).status()).isEqualTo("VALIDATED");
    assertThat(in.store.of(NPI_2).status()).isEqualTo("VALIDATED");
  }

  // ---- process: re-run of a business date (MAM-57 AC-8)
  // -------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-8] a record an earlier run of the same business date processed is skipped: no application, no row, no event")
  void alreadyProcessedIsSkipped() {
    String done = AppInputFile.valid(NPI_1);
    in.runs.alreadyProcessed.add(done);
    in.process(done, AppInputFile.valid(NPI_4));
    assertThat(in.store.saved).extracting(StoredApplication::npi).containsExactly(NPI_4);
    assertThat(in.runs.lastCounts()).containsExactly(2, 1, 0, 1);
    assertThat(in.runs.errorRows).isEmpty();
    assertThat(in.outbox.sent).extracting(IntakeFakes.Sent::subject).containsExactly(NPI_4);
    assertThat(in.runs.attachedRecords).containsExactly(AppInputFile.valid(NPI_4));
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-8] a processed rejected record is skipped too: it gets no second APPERROR row")
  void processedRejectedRecordIsSkipped() {
    String done = AppInputFile.noTaxonomy(NPI_2);
    in.runs.alreadyProcessed.add(done);
    in.process(done);
    assertThat(in.store.saved).isEmpty();
    assertThat(in.runs.errorRows).isEmpty();
    assertThat(in.runs.lastCounts()).containsExactly(1, 0, 0, 1);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-8] every stored record is tied to its run with its application id and its 200-byte record")
  void storedRecordsAreAttached() {
    String valid = AppInputFile.valid(NPI_1);
    String rejected = AppInputFile.noTaxonomy(NPI_2);
    in.process(valid, rejected);
    assertThat(in.runs.attachedRecords).containsExactly(valid, rejected);
    assertThat(in.runs.attachedApplications)
        .containsExactly(
            in.store.saved.get(0).applicationId(), in.store.saved.get(1).applicationId());
    assertThat(in.runs.attachedRecords).allSatisfy(r -> assertThat(r).hasSize(200));
  }

  // ---- process: failures (MAM-57 AC-5, AC-6)
  // ---------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-5] a record of 150 bytes ends the run: the error names record 3 and its length, the counts keep the record as errored")
  void shortRecordEndsTheRun() {
    byte[] file =
        AppInputFile.lines(
            AppInputFile.valid(NPI_1),
            AppInputFile.valid(NPI_2),
            AppInputFile.cut(AppInputFile.valid(NPI_3), 150),
            AppInputFile.valid(NPI_4));
    assertThatThrownBy(() -> in.load.process(UUID.randomUUID(), DATE, file, "admin1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 3")
        .hasMessageContaining("150 bytes");
    assertThat(in.runs.lastCounts()).containsExactly(3, 2, 1, 0);
    assertThat(in.store.saved).extracting(StoredApplication::npi).containsExactly(NPI_1, NPI_2);
    assertThat(in.runs.calls).doesNotContain("complete");
  }

  @ParameterizedTest(name = "[MAM-57 AC-5] a record of {0} bytes is unreadable")
  @ValueSource(ints = {0, 1, 129, 130, 199, 201, 250})
  void wrongLengthIsUnreadable(int length) {
    String record = (AppInputFile.valid(NPI_1) + "x".repeat(60)).substring(0, length);
    byte[] file = AppInputFile.lines(AppInputFile.valid(NPI_2), record);
    assertThatThrownBy(() -> in.load.process(UUID.randomUUID(), DATE, file, "admin1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2 is " + length + " bytes, not 200");
    assertThat(in.runs.lastCounts()).containsExactly(2, 1, 1, 0);
  }

  @Test
  @DisplayName("[MAM-57 AC-5] a record of exactly 200 bytes is readable")
  void exactly200IsReadable() {
    in.process(AppInputFile.valid(NPI_1));
    assertThat(in.runs.calls).contains("complete");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-6] a storage failure ends the run: the error names record 2 and its NPI, earlier results are kept, later records are not processed")
  void storageFailureEndsTheRun() {
    in.store.failForNpi = NPI_1;
    byte[] file =
        AppInputFile.lines(
            AppInputFile.noTaxonomy(NPI_2), AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_3));
    assertThatThrownBy(() -> in.load.process(UUID.randomUUID(), DATE, file, "admin1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2")
        .hasMessageContaining("NPI " + NPI_1)
        .hasCauseInstanceOf(IllegalStateException.class);
    assertThat(in.runs.lastCounts()).containsExactly(2, 0, 2, 0);
    assertThat(in.store.saved).extracting(StoredApplication::npi).containsExactly(NPI_2);
    assertThat(in.runs.errorRows).containsExactly(AppInputFile.errorRow(NPI_2, "IN"));
    assertThat(in.outbox.sent).extracting(IntakeFakes.Sent::subject).containsExactly(NPI_2);
    assertThat(in.runs.calls).doesNotContain("complete");
  }

  // ---- failed: the run is marked FAILED
  // --------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-6] failed marks the run FAILED with the reason at the clock time and logs it as an error")
  void failedMarksTheRun() {
    UUID id = UUID.randomUUID();
    in.load.failed(id, DATE, "record 2 NPI 1003000111 could not be stored");
    assertThat(in.runs.failedReason).isEqualTo("record 2 NPI 1003000111 could not be stored");
    assertThat(in.runs.failedAt).isEqualTo(NOW);
    assertThat(logged(Level.ERROR))
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains(id.toString())
                    .contains("2026-03-31")
                    .contains("record 2 NPI 1003000111 could not be stored"));
  }

  // ---- process: reading PRVRECRD (MAM-56 AC-3)
  // ---------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-56 AC-3] an individual (type IN) is read from bytes 1-31 and 51-117: last name, first name, licence number, licence state")
  void individualFields() {
    in.process(
        AppInputFile.individual(
            NPI_1, "M00000111", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX"));
    StoredApplication a = in.store.of(NPI_1);
    assertThat(a.medicaidId()).isEqualTo("M00000111");
    assertThat(a.providerType()).isEqualTo("IN");
    assertThat(a.taxonomy()).isEqualTo("207Q00000X");
    assertThat(a.individual())
        .isEqualTo(new NewApplication.Individual("RIVERA", "MARIA", "MD12345", "TX"));
    assertThat(a.organization()).isNull();
  }

  @ParameterizedTest(
      name =
          "[MAM-56 AC-3] type {0} is read as an organisation: legal name 51-90, DBA name 91-120, owner NPI 121-130")
  @ValueSource(strings = {"GR", "FA"})
  void organizationFields(String type) {
    in.process(
        AppInputFile.organization(
            NPI_2,
            "M00000222",
            type,
            "207Q00000X",
            "EXCLUDED CLINIC LLC",
            "DBA CLINIC",
            "1990000111"));
    StoredApplication a = in.store.of(NPI_2);
    assertThat(a.providerType()).isEqualTo(type);
    assertThat(a.organization())
        .isEqualTo(
            new NewApplication.Organization("EXCLUDED CLINIC LLC", "DBA CLINIC", "1990000111"));
    assertThat(a.individual()).isNull();
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a blank DBA name is no DBA name (null), the other fields are kept")
  void blankDbaIsNull() {
    in.process(
        AppInputFile.organization(
            NPI_2, "M00000222", "GR", "207Q00000X", "EXCLUDED CLINIC LLC", "", "1990000111"));
    assertThat(in.store.of(NPI_2).organization())
        .isEqualTo(new NewApplication.Organization("EXCLUDED CLINIC LLC", null, "1990000111"));
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a blank Medicaid ID is no Medicaid ID (null)")
  void blankMedicaidId() {
    in.process(
        AppInputFile.individual(NPI_1, "", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX"));
    assertThat(in.store.of(NPI_1).medicaidId()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] an unknown type code has neither the individual nor the organisation view")
  void unknownTypeHasNoView() {
    in.process(AppInputFile.ofType(NPI_2, "XX"));
    StoredApplication a = in.store.of(NPI_2);
    assertThat(a.individual()).isNull();
    assertThat(a.organization()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] bytes 32-50 (tier, status, flag, dates) and 131-200 are not taken over: the stored application and the event carry only the PRVRECRD key fields")
  void legacyBytesAreNotTakenOver() {
    String record = AppInputFile.valid(NPI_1);
    record = AppInputFile.with(record, 32, "HAP");
    record = AppInputFile.with(record, 35, "2026011520310115");
    record = AppInputFile.with(record, 131, "RESERVED TEXT");
    in.process(record);
    StoredApplication a = in.store.of(NPI_1);
    assertThat(a.status()).isEqualTo("VALIDATED");
    assertThat(a.toString()).doesNotContain("RESERVED").doesNotContain("20310115");
    assertThat(in.outbox.sent.get(0).data().toString())
        .doesNotContain("RESERVED")
        .doesNotContain("20310115");
    assertThat(in.outbox.sent.get(0).data())
        .isEqualTo(
            new ApplicationValidatedData(
                a.applicationId(),
                NPI_1,
                "M00000111",
                "IN",
                "207Q00000X",
                new NewApplication.Individual("RIVERA", "MARIA", "MD12345", "TX"),
                null));
  }

  @Test
  @DisplayName("[MAM-56 AC-3] values are read without their trailing blanks")
  void valuesAreTrimmed() {
    in.process(AppInputFile.valid(NPI_1));
    StoredApplication a = in.store.of(NPI_1);
    assertThat(a.npi()).isEqualTo(NPI_1).hasSize(10);
    assertThat(a.individual().lastName()).isEqualTo("RIVERA");
    assertThat(a.individual().licenseState()).isEqualTo("TX");
  }
}
