package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-56 and MAM-57: how the intake reads a file and what it stores, beyond the Cucumber scenarios.
 * The file may have LF or CR LF line ends or none (blocks of 200 bytes); a rejected record is kept
 * with its raw record and its errors; the start of a run by SYSTEM or ADMIN is the same load.
 */
class IntakeRunLoadTest extends IntakeIntegrationTest {

  static Stream<Arguments> fileStyles() {
    String[] r = {
      AppInputFile.valid("1003000111"),
      AppInputFile.valid("1003000222"),
      AppInputFile.valid("1003000333")
    };
    return Stream.of(
        Arguments.of("LF line ends", AppInputFile.lines(r)),
        Arguments.of("CR LF line ends", AppInputFile.crlf(r)),
        Arguments.of("no final line end", AppInputFile.linesWithoutFinalEnd(r)),
        Arguments.of("no line ends (blocks of 200 bytes)", AppInputFile.blocks(r)));
  }

  @ParameterizedTest(name = "[MAM-56 AC-3] a file with {0} is read as three records")
  @MethodSource("fileStyles")
  void everyFileStyleIsRead(String style, byte[] file) throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, file, DATE);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(3);
    assertThat(run.path("counts").path("written").asInt()).isEqualTo(3);
    assertThat(count("SELECT count(*) FROM application WHERE source = 'LEGACY_FILE'")).isEqualTo(3);
  }

  @Test
  @DisplayName("[MAM-57 AC-1] an empty file completes with every count 0 and an empty error file")
  void emptyFile() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, new byte[0], DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    JsonNode counts = run.path("counts");
    assertThat(counts.path("read").asInt()).isZero();
    assertThat(counts.path("written").asInt()).isZero();
    assertThat(counts.path("errored").asInt()).isZero();
    assertThat(counts.path("skipped").asInt()).isZero();
    assertThat(IntakeApi.errorFileOf(mvc, run.path("runId").asText())).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-57 AC-5] a record of {0} bytes ends the run FAILED naming record 2")
  @ValueSource(ints = {0, 130, 199, 201})
  void wrongLength(int length) throws Exception {
    String bad = (AppInputFile.valid("1003000222") + "x".repeat(60)).substring(0, length);
    JsonNode run =
        IntakeApi.runToEnd(
            mvc,
            json,
            AppInputFile.lines(AppInputFile.valid("1003000111"), bad, AppInputFile.valid("1003000333")),
            DATE);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).contains("record 2").contains(length + " bytes");
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(2);
    assertThat(applications("1003000333")).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] a fixed-block file whose last block is short fails naming that record")
  void shortLastBlock() throws Exception {
    byte[] file =
        (AppInputFile.valid("1003000111") + AppInputFile.valid("1003000222").substring(0, 120))
            .getBytes(StandardCharsets.ISO_8859_1);
    JsonNode run = IntakeApi.runToEnd(mvc, json, file, DATE);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).contains("record 2").contains("120 bytes");
    assertThat(statusOf("1003000111")).isEqualTo("VALIDATED");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] each stored application keeps its raw 200-byte record and a link to its run (D-025)")
  void rawRecordAndRunAreKept() throws Exception {
    String record = AppInputFile.with(AppInputFile.valid("1003000111"), 131, "RESERVED TEXT");
    JsonNode run = IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record), DATE);
    assertThat(
            jdbc.queryForObject(
                "SELECT raw_record FROM application WHERE npi = '1003000111'", String.class))
        .isEqualTo(record);
    assertThat(
            jdbc.queryForObject(
                "SELECT r.public_id::text FROM application a JOIN intake_run r ON r.id = a.intake_run_id"
                    + " WHERE a.npi = '1003000111'",
                String.class))
        .isEqualTo(run.path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-56 AC-4] a rejected record keeps its raw record and its run link too")
  void rejectedRecordKeepsRawRecord() throws Exception {
    String record = AppInputFile.noTaxonomy("1003000222");
    IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record), DATE);
    assertThat(
            jdbc.queryForObject(
                "SELECT raw_record FROM application WHERE npi = '1003000222'", String.class))
        .isEqualTo(record);
  }

  @ParameterizedTest(name = "[MAM-56 AC-4] an NPI of [{0}] is E101: REJECTED, one error row with the NPI as read")
  @ValueSource(strings = {"ABCDEFGHIJ", "100300011", "100300011A", "1003-00011", " 100300011"})
  void malformedNpiIsE101(String npi) throws Exception {
    String record =
        AppInputFile.individual(
            npi, "M00000999", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
    JsonNode run = IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record), DATE);
    assertThat(run.path("counts").path("errored").asInt()).isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT trim(e.code) || '/' || e.field FROM application_error e", String.class))
        .contains("E101/npi");
    List<String> rows = AppInputFile.rows(IntakeApi.errorFileOf(mvc, run.path("runId").asText()));
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).startsWith(String.format("%-10s TYPE:IN ERR:VALIDATION-FAILED", npi));
  }

  @Test
  @DisplayName(
      "[Q-095] a staff-run intake loads an individual under any NPI: it is not refused for not being the starter's own")
  void staffRunIsNotBoundToAnOwnNpi() throws Exception {
    JsonNode run =
        IntakeApi.runToEnd(
            mvc, json, AppInputFile.lines(AppInputFile.valid("1003000555")), DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(statusOf("1003000555")).isEqualTo("VALIDATED");
  }

  @Test
  @DisplayName(
      "[Q-100] an individual record with a blank NPI is REJECTED E101 and counted errored: the run completes, it is not refused")
  void blankNpiIsRejectedNotRefused() throws Exception {
    JsonNode run =
        IntakeApi.runToEnd(mvc, json, AppInputFile.lines(AppInputFile.noNpi()), DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("errored").asInt()).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM application WHERE status = 'REJECTED' AND npi IS NULL"))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-7] a run started by SYSTEM loads the file exactly like one started by ADMIN")
  void systemRunLoadsTheFile() throws Exception {
    var started =
        IntakeApi.start(
            mvc,
            PemJwt.system(),
            AppInputFile.lines(AppInputFile.valid("1003000111"), AppInputFile.noTaxonomy("1003000222")),
            DATE);
    assertThat(started.getResponse().getStatus()).isEqualTo(202);
    JsonNode run =
        IntakeApi.await(
            mvc, json, json.readTree(started.getResponse().getContentAsString()).path("runId").asText());
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("written").asInt()).isEqualTo(1);
    assertThat(run.path("counts").path("errored").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-1] two runs started one after the other each report their own counts")
  void twoRunsHaveTheirOwnCounts() throws Exception {
    String first =
        IntakeApi.startAsAdmin(
            mvc,
            json,
            AppInputFile.lines(
                AppInputFile.valid("1003000111"),
                AppInputFile.valid("1003000222"),
                AppInputFile.valid("1003000333")),
            DATE);
    String second =
        IntakeApi.startAsAdmin(
            mvc, json, AppInputFile.lines(AppInputFile.valid("1003000777")), "2026-04-01");
    JsonNode a = IntakeApi.await(mvc, json, first);
    JsonNode b = IntakeApi.await(mvc, json, second);
    assertThat(a.path("counts").path("read").asInt()).isEqualTo(3);
    assertThat(a.path("counts").path("written").asInt()).isEqualTo(3);
    assertThat(b.path("counts").path("read").asInt()).isEqualTo(1);
    assertThat(b.path("counts").path("written").asInt()).isEqualTo(1);
    assertThat(a.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(b.path("status").asText()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] a duplicate within the file is skipped even when its raw record equals the first one's")
  void identicalRecordsInOneFile() throws Exception {
    String record = AppInputFile.valid("1003000111");
    JsonNode run =
        IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record, record), DATE);
    assertThat(run.path("counts").path("written").asInt()).isEqualTo(1);
    assertThat(run.path("counts").path("skipped").asInt()).isEqualTo(1);
    assertThat(applications("1003000111")).isEqualTo(2);
    assertThat(
            count("SELECT count(*) FROM application WHERE npi = '1003000111' AND status = 'VALIDATED'"))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] a record that fails and is loaded again under another business date is rejected again, with a new error row")
  void rejectedRecordUnderAnotherDate() throws Exception {
    String bad = AppInputFile.noTaxonomy("1003000222");
    IntakeApi.runToEnd(mvc, json, AppInputFile.lines(bad), DATE);
    JsonNode again = IntakeApi.runToEnd(mvc, json, AppInputFile.lines(bad), "2026-04-01");
    assertThat(again.path("counts").path("errored").asInt()).isEqualTo(1);
    assertThat(again.path("counts").path("skipped").asInt()).isZero();
    assertThat(AppInputFile.rows(IntakeApi.errorFileOf(mvc, again.path("runId").asText())))
        .containsExactly(AppInputFile.errorRow("1003000222", "IN"));
  }
}
