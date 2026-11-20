package gov.medicaid.pem.tmsis.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The values of the application layer that the contracts fix: the run statuses of BatchRun.status,
 * the page size of provider-service's searchProviders (at most 200), and the shapes of the records
 * that carry a run, an event and a provider.
 */
class ContractConstantsTest {

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the run statuses are the contract's enum: RUNNING, COMPLETED and FAILED")
  void runStatuses() {
    assertThat(ExtractRun.RUNNING).isEqualTo("RUNNING");
    assertThat(ExtractRun.COMPLETED).isEqualTo("COMPLETED");
    assertThat(ExtractRun.FAILED).isEqualTo("FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] providers are read in pages of 200, the most provider-service's search allows")
  void pageSizeIs200() {
    assertThat(ActiveProviders.PAGE_SIZE).isEqualTo(200);
  }

  @Test
  @DisplayName("[MAM-71 AC-5] a page holds its items and the total number of ACTIVE providers")
  void pageHoldsItemsAndTotal() {
    ProviderRow row = new ProviderRow("1003000111", "M00000111", "A", LocalDate.parse("2026-01-15"));
    ActiveProviders.Page page = new ActiveProviders.Page(java.util.List.of(row), 450);
    assertThat(page.items()).containsExactly(row);
    assertThat(page.total()).isEqualTo(450);
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-1] a provider row carries the NPI, Medicaid ID, enrolment status and enrolment date")
  void providerRowFields() {
    ProviderRow row = new ProviderRow("1003000111", "M00000111", "A", LocalDate.parse("2026-01-15"));
    assertThat(row.npi()).isEqualTo("1003000111");
    assertThat(row.medicaidId()).isEqualTo("M00000111");
    assertThat(row.enrollStatus()).isEqualTo("A");
    assertThat(row.effectiveDate()).isEqualTo(LocalDate.parse("2026-01-15"));
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event data holds the run id, business date, period and row count of the contract")
  void eventDataFields() {
    UUID id = UUID.randomUUID();
    ExtractProducedData data = new ExtractProducedData(id, LocalDate.parse("2026-03-31"), "26090", 2);
    assertThat(data.runId()).isEqualTo(id);
    assertThat(data.businessDate()).isEqualTo(LocalDate.parse("2026-03-31"));
    assertThat(data.periodYyddd()).isEqualTo("26090");
    assertThat(data.rowCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-2] a run carries its counts: rows, future enrolments and the archive generation")
  void runFields() {
    UUID id = UUID.randomUUID();
    Instant start = Instant.parse("2026-01-15T10:00:00Z");
    Instant end = Instant.parse("2026-01-15T10:00:05Z");
    ExtractRun run =
        new ExtractRun(
            id, LocalDate.parse("2026-03-31"), "26090", ExtractRun.COMPLETED, 2, 1, 3, null, start, end);
    assertThat(run.runId()).isEqualTo(id);
    assertThat(run.rows()).isEqualTo(2);
    assertThat(run.futureEnrolments()).isEqualTo(1);
    assertThat(run.archiveGeneration()).isEqualTo(3);
    assertThat(run.startedAt()).isEqualTo(start);
    assertThat(run.endedAt()).isEqualTo(end);
  }

  @Test
  @DisplayName("[MAM-71 AC-2] the status of a T-MSIS record is the letter A")
  void recordStatusConstant() {
    assertThat(TmsisRecord.ACTIVE).isEqualTo("A");
  }
}
