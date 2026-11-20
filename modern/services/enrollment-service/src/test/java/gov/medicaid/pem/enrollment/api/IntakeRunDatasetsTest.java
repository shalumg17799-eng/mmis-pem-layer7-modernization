package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-56 and MAM-57 with the synthetic APPINPUT datasets of MAM-44 (modern/testdata): the files as
 * the legacy layout writes them, run through the endpoint. The baseline file has valid applications
 * and one record for each of E101, E102, E104, E103 and E102 with E103; the scenarios file holds
 * the eight applications of aws-transform/test-data; the bad-length file has a record of 199 bytes.
 */
class IntakeRunDatasetsTest extends IntakeIntegrationTest {

  static final String BASELINE = "fixed-width/appinput-baseline.dat";
  static final String SCENARIOS = "scenarios/appinput-scenarios.dat";
  static final String BAD_LENGTH = "negative/appinput-bad-length.dat";

  static String npi(String record) {
    return record.substring(0, 10).trim();
  }

  static String type(String record) {
    return record.substring(19, 21);
  }

  JsonNode run(String dataset, String date) throws Exception {
    return IntakeApi.runToEnd(mvc, json, AppInputFile.dataset(dataset), date);
  }

  List<String> errorRowsOf(JsonNode run) throws Exception {
    return AppInputFile.rows(IntakeApi.errorFileOf(mvc, run.path("runId").asText()));
  }

  List<String> errorCodes(String npi) {
    return jdbc.queryForList(
        "SELECT trim(e.code) || '/' || e.field FROM application_error e JOIN application a ON a.id = e.application_id"
            + " WHERE a.npi = ? AND a.status = 'REJECTED' ORDER BY a.id, e.id",
        String.class,
        npi);
  }

  // ---- appinput-baseline.dat -----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-2] the baseline file: read 8, written 3, errored 4, skipped 1, COMPLETED (read = written + errored + skipped)")
  void baselineCounts() throws Exception {
    JsonNode run = run(BASELINE, "2026-01-15");
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    JsonNode counts = run.path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(8);
    assertThat(counts.path("written").asInt()).isEqualTo(3);
    assertThat(counts.path("errored").asInt()).isEqualTo(4);
    assertThat(counts.path("skipped").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] the baseline file: records 1 to 3 are VALIDATED, 4 to 8 REJECTED, each with exactly the errors of its check")
  void baselineStatusesAndErrors() throws Exception {
    run(BASELINE, "2026-01-15");
    List<String> records = AppInputFile.datasetRecords(BASELINE);
    assertThat(records).hasSize(8);
    for (int i : new int[] {0, 1, 2}) {
      assertThat(statusOf(npi(records.get(i)))).isEqualTo("VALIDATED");
    }
    assertThat(errorCodes(npi(records.get(4)))).containsExactly("E102/providerType");
    assertThat(errorCodes(npi(records.get(5)))).containsExactly("E104/taxonomy");
    // record 7 repeats the NPI of record 1; record 8 repeats the NPI of record 2 and has type ZZ
    assertThat(
            jdbc.queryForList(
                "SELECT trim(e.code) || '/' || e.field FROM application_error e JOIN application a"
                    + " ON a.id = e.application_id WHERE a.npi = ? AND a.status = 'REJECTED' ORDER BY e.id",
                String.class,
                npi(records.get(6))))
        .containsExactly("E103/npi");
    assertThat(
            jdbc.queryForList(
                "SELECT trim(e.code) || '/' || e.field FROM application_error e JOIN application a"
                    + " ON a.id = e.application_id WHERE a.npi = ? AND a.status = 'REJECTED' ORDER BY e.id",
                String.class,
                npi(records.get(7))))
        .containsExactlyInAnyOrder("E103/npi", "E102/providerType");
    assertThat(
            jdbc.queryForList(
                "SELECT trim(e.code) || '/' || e.field FROM application_error e JOIN application a"
                    + " ON a.id = e.application_id WHERE a.npi IS NULL ORDER BY e.id",
                String.class))
        .containsExactly("E101/npi");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-6] the baseline file: the error file has one 250-character row for each of records 4 to 8, NPI and type as read, in input order")
  void baselineErrorFile() throws Exception {
    JsonNode run = run(BASELINE, "2026-01-15");
    List<String> records = AppInputFile.datasetRecords(BASELINE);
    List<String> expected = new ArrayList<>();
    for (int i = 3; i < 8; i++) {
      expected.add(AppInputFile.errorRow(records.get(i).substring(0, 10), type(records.get(i))));
    }
    assertThat(errorRowsOf(run)).containsExactlyElementsOf(expected);
    assertThat(expected.get(0)).startsWith("           TYPE:IN ERR:VALIDATION-FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] the baseline file: ApplicationValidated for records 1 to 3, ApplicationRejected for 5 to 8, none for the record without an NPI")
  void baselineEvents() throws Exception {
    run(BASELINE, "2026-01-15");
    List<String> records = AppInputFile.datasetRecords(BASELINE);
    assertThat(subjects("ApplicationValidated"))
        .containsExactlyInAnyOrder(
            npi(records.get(0)), npi(records.get(1)), npi(records.get(2)));
    assertThat(subjects("ApplicationRejected"))
        .containsExactlyInAnyOrder(
            npi(records.get(4)), npi(records.get(5)), npi(records.get(6)), npi(records.get(7)));
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-8] the baseline file again for the same business date: read 8, written 0, errored 0, skipped 8, empty error file, no new event")
  void baselineTwice() throws Exception {
    run(BASELINE, "2026-01-15");
    int events = count("SELECT count(*) FROM outbox_event");
    int applications = count("SELECT count(*) FROM application");
    JsonNode again = run(BASELINE, "2026-01-15");
    JsonNode counts = again.path("counts");
    assertThat(again.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(counts.path("read").asInt()).isEqualTo(8);
    assertThat(counts.path("written").asInt()).isZero();
    assertThat(counts.path("errored").asInt()).isZero();
    assertThat(counts.path("skipped").asInt()).isEqualTo(8);
    assertThat(errorRowsOf(again)).isEmpty();
    assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(events);
    assertThat(count("SELECT count(*) FROM application")).isEqualTo(applications);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] the baseline file under another business date: the valid applications are duplicates (E103, skipped) with an error row each")
  void baselineUnderAnotherDate() throws Exception {
    run(BASELINE, "2026-01-15");
    JsonNode again = run(BASELINE, "2026-01-16");
    JsonNode counts = again.path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(8);
    assertThat(counts.path("written").asInt()).isZero();
    assertThat(counts.path("skipped").asInt()).isEqualTo(4);
    assertThat(counts.path("errored").asInt()).isEqualTo(4);
    assertThat(errorRowsOf(again)).hasSize(8);
  }

  // ---- appinput-scenarios.dat -----------------------------------------------------------------

  @Test
  @DisplayName("[MAM-57 AC-1] the scenarios file: all eight applications are written, nothing errored or skipped")
  void scenariosCounts() throws Exception {
    JsonNode run = run(SCENARIOS, "2026-01-15");
    JsonNode counts = run.path("counts");
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(counts.path("read").asInt()).isEqualTo(8);
    assertThat(counts.path("written").asInt()).isEqualTo(8);
    assertThat(counts.path("errored").asInt()).isZero();
    assertThat(counts.path("skipped").asInt()).isZero();
    assertThat(errorRowsOf(run)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] the scenarios file: 1003000111 is the individual RIVERA, first name 'MARIA MD', licence MD12345 TX")
  void scenariosIndividual() throws Exception {
    run(SCENARIOS, "2026-01-15");
    assertThat(
            jdbc.queryForList(
                "SELECT trim(last_name), trim(first_name), trim(license_no), trim(license_state),"
                    + " trim(provider_type), trim(taxonomy), trim(medicaid_id), source"
                    + " FROM application WHERE npi = '1003000111'"))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row.values())
                    .containsExactly(
                        "RIVERA", "MARIA MD", "MD12345", "TX", "IN", "207Q00000X", "986485495",
                        "LEGACY_FILE"));
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] the scenarios file: 1003000444 is the group EXCLUDED CLINIC LLC with owner NPI 1990000111 (bytes 121-130 are read)")
  void scenariosOrganization() throws Exception {
    run(SCENARIOS, "2026-01-15");
    assertThat(
            jdbc.queryForMap(
                "SELECT trim(legal_name) AS legal, trim(dba_name) AS dba, trim(owner_npi) AS owner,"
                    + " last_name FROM application WHERE npi = '1003000444'"))
        .containsEntry("legal", "EXCLUDED CLINIC LLC")
        .containsEntry("owner", "1990000111")
        .containsEntry("dba", null)
        .containsEntry("last_name", null);
    assertThat(
            jdbc.queryForObject(
                "SELECT trim(owner_npi) FROM application WHERE npi = '1003000222'", String.class))
        .isEqualTo("1990000999");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] the scenarios file: a facility with no DBA name and no owner (1003000333) keeps only its legal name")
  void scenariosFacilityWithoutOwner() throws Exception {
    run(SCENARIOS, "2026-01-15");
    assertThat(
            jdbc.queryForMap(
                "SELECT trim(legal_name) AS legal, dba_name, owner_npi, trim(provider_type) AS type"
                    + " FROM application WHERE npi = '1003000333'"))
        .containsEntry("legal", "SUNSET HOSPICE INC")
        .containsEntry("dba_name", null)
        .containsEntry("owner_npi", null)
        .containsEntry("type", "FA");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] the scenarios file: record 1003000666 with no name keeps its licence number and state only")
  void scenariosNoName() throws Exception {
    run(SCENARIOS, "2026-01-15");
    assertThat(
            jdbc.queryForMap(
                "SELECT last_name, first_name, trim(license_no) AS licence, trim(license_state) AS state"
                    + " FROM application WHERE npi = '1003000666'"))
        .containsEntry("last_name", null)
        .containsEntry("first_name", null)
        .containsEntry("licence", "XX00000")
        .containsEntry("state", "TX");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] every application of the scenarios file keeps its 200-byte record and the link to the run")
  void scenariosKeepTheRawRecord() throws Exception {
    JsonNode run = run(SCENARIOS, "2026-01-15");
    for (String record : AppInputFile.datasetRecords(SCENARIOS)) {
      assertThat(
              jdbc.queryForList(
                  "SELECT raw_record, r.public_id::text FROM application a JOIN intake_run r ON r.id = a.intake_run_id"
                      + " WHERE a.npi = ?",
                  record.substring(0, 10)))
          .extracting(row -> row.get("raw_record"), row -> row.get("public_id"))
          .containsExactly(tuple(record, run.path("runId").asText()));
    }
  }

  @Test
  @DisplayName("[MAM-56 AC-4] the scenarios file: eight ApplicationValidated events, one per NPI, none rejected")
  void scenariosEvents() throws Exception {
    run(SCENARIOS, "2026-01-15");
    List<String> npis =
        AppInputFile.datasetRecords(SCENARIOS).stream().map(IntakeRunDatasetsTest::npi).toList();
    assertThat(subjects("ApplicationValidated")).containsExactlyInAnyOrderElementsOf(npis);
    assertThat(subjects("ApplicationRejected")).isEmpty();
  }

  // ---- appinput-bad-length.dat -------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-57 AC-5] the bad-length file: the run ends FAILED naming record 3 and its 199 bytes, with an end time")
  void badLengthFails() throws Exception {
    JsonNode run = run(BAD_LENGTH, "2026-01-15");
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).contains("record 3").contains("199 bytes");
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] the bad-length file: counts read 3, written 1, errored 2 (the rejected record 2 and the unreadable record 3)")
  void badLengthCounts() throws Exception {
    JsonNode counts = run(BAD_LENGTH, "2026-01-15").path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(3);
    assertThat(counts.path("written").asInt()).isEqualTo(1);
    assertThat(counts.path("errored").asInt()).isEqualTo(2);
    assertThat(counts.path("skipped").asInt()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] the bad-length file: records 1 and 2 keep their results and record 4 is never processed")
  void badLengthKeepsEarlierResults() throws Exception {
    run(BAD_LENGTH, "2026-01-15");
    List<String> records = AppInputFile.datasetRecords(BAD_LENGTH);
    assertThat(records).hasSize(4);
    assertThat(statusOf(npi(records.get(0)))).isEqualTo("VALIDATED");
    assertThat(count("SELECT count(*) FROM application WHERE status = 'REJECTED' AND npi IS NULL"))
        .isEqualTo(1);
    assertThat(applications(npi(records.get(3)))).isZero();
    assertThat(applications(npi(records.get(2)))).isZero();
  }
}
