package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.LogCapture;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.Tmsisout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-78: while the file is prepared, an ACTIVE provider whose enrolment year is later than the
 * year of the run's business date is logged once with the legacy text 'PRVTMS01: FUTURE ENROLLMENT?
 * NPI &lt;npi&gt;' and counted in counts.futureEnrolments; the warning never excludes the provider
 * or changes its record (R-PRVTMS01-cbl-00005, -00006, -00035 to -00037, D-004, D-020). The warning
 * is a WARN event of the job thread; the tests collect every log event.
 */
@Timeout(120)
class FutureEnrolmentApiTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  static String warning(String npi) {
    return "PRVTMS01: FUTURE ENROLLMENT? NPI " + npi;
  }

  /** The run for the date, awaited, with the WARN lines that were logged while it ran. */
  record Outcome(JsonNode run, List<String> warnings, List<String> npis) {}

  Outcome run(String date) throws Exception {
    try (LogCapture log = LogCapture.open()) {
      JsonNode run = api.runToEnd(date);
      assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
      List<String> warnings =
          log.messages(Level.WARN).stream()
              .filter(m -> m.startsWith("PRVTMS01: FUTURE ENROLLMENT?"))
              .toList();
      return new Outcome(run, warnings, Tmsisout.npis(api.file(run.path("runId").asText())));
    }
  }

  static int flagged(JsonNode run) {
    return run.path("counts").path("futureEnrolments").asInt(-1);
  }

  // ---- AC-1 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-1] NPI 1003000777 ACTIVE with effectiveDate 2027-01-15, business date 2026-03-31: exactly one log line 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777' and counts.futureEnrolments 1")
  void laterYearIsWarnedAndCounted() throws Exception {
    TestProviderService.holds(Prov.active("1003000777").effective("2027-01-15"));
    Outcome o = run(DATE);
    assertThat(o.warnings()).containsExactly(warning("1003000777"));
    assertThat(flagged(o.run())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-1] the count of a run without a flagged provider is 0, and nothing is logged")
  void noFlaggedProvider() throws Exception {
    TestProviderService.holds(Prov.active("1003000111").effective("2026-03-31"));
    Outcome o = run(DATE);
    assertThat(o.warnings()).isEmpty();
    assertThat(flagged(o.run())).isZero();
  }

  @Test
  @DisplayName("[MAM-78 AC-1] the count is in the list of runs too, as counts.futureEnrolments")
  void countIsListed() throws Exception {
    TestProviderService.holds(Prov.active("1003000777").effective("2027-01-15"));
    Outcome o = run(DATE);
    assertThat(flagged(api.listed(o.run().path("runId").asText()))).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-1] each flagged provider is logged exactly once and counted once, also across chunks of 200: 45 of 450 flagged")
  void manyFlaggedAcrossChunks() throws Exception {
    List<Prov> all = new ArrayList<>();
    List<String> flaggedNpis = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      String npi = String.format("1003%06d", 1000 + i);
      boolean future = i % 10 == 0;
      all.add(Prov.active(npi).effective(future ? "2027-06-01" : "2026-02-01"));
      if (future) {
        flaggedNpis.add(npi);
      }
    }
    TestProviderService.holds(all);
    Outcome o = run(DATE);
    assertThat(flagged(o.run())).isEqualTo(45);
    assertThat(o.warnings())
        .containsExactlyInAnyOrderElementsOf(flaggedNpis.stream().map(n -> warning(n)).toList());
    assertThat(o.npis()).hasSize(450);
  }

  // ---- AC-2 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-2] the flagged provider is still in the file with status A and period 26090, and counted in counts.rows")
  void flaggedProviderStillReported() throws Exception {
    TestProviderService.holds(
        Prov.active("1003000111").effective("2026-03-31"),
        Prov.active("1003000777").effective("2027-01-15"));
    Outcome o = run(DATE);
    byte[] file = api.file(o.run().path("runId").asText());
    List<String> records = Tmsisout.records(file);
    assertThat(records).hasSize(2);
    String flagged = records.get(1);
    assertThat(Tmsisout.npi(flagged)).isEqualTo("1003000777");
    assertThat(Tmsisout.status(flagged)).isEqualTo("A");
    assertThat(Tmsisout.period(flagged)).isEqualTo("26090");
    assertThat(o.run().path("counts").path("rows").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-2] the record of a flagged provider is byte for byte the record of an unflagged one with the same ids")
  void flaggedRecordIsUnchanged() throws Exception {
    TestProviderService.holds(Prov.active("1003000777").effective("2027-01-15"));
    String flaggedRecord =
        Tmsisout.records(api.file(api.runToEnd(DATE).path("runId").asText())).get(0);
    TestProviderService.holds(Prov.active("1003000777").effective("2026-01-15"));
    String normalRecord =
        Tmsisout.records(api.file(api.runToEnd(DATE).path("runId").asText())).get(0);
    assertThat(flaggedRecord).isEqualTo(normalRecord);
    assertThat(flaggedRecord).isEqualTo(Tmsisout.expected("M00000777", "1003000777", "26090"));
  }

  // ---- AC-3 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-3] effective 2026-03-31, 2026-12-31 and 2027-01-01 against 2026-03-31: only 1003000777 (2027) is flagged, counts.futureEnrolments is 1")
  void onlyAStrictlyLaterYearIsFlagged() throws Exception {
    TestProviderService.holds(
        Prov.active("1003000111").effective("2026-03-31"),
        Prov.active("1003000333").effective("2026-12-31"),
        Prov.active("1003000777").effective("2027-01-01"));
    Outcome o = run(DATE);
    assertThat(o.warnings()).containsExactly(warning("1003000777"));
    assertThat(flagged(o.run())).isEqualTo(1);
    assertThat(o.npis()).containsExactly("1003000111", "1003000333", "1003000777");
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-3] the reference year is the business date's, not the clock's: business date 2027-03-01 does not flag 2027-01-01, business date 2025-12-31 does")
  void referenceYearIsTheBusinessDates() throws Exception {
    TestProviderService.holds(Prov.active("1003000777").effective("2027-01-01"));
    TestClock.set("2026-01-15T10:00:00Z");
    Outcome later = run("2027-03-01");
    assertThat(later.warnings()).isEmpty();
    assertThat(flagged(later.run())).isZero();
    Outcome earlier = run("2025-12-31");
    assertThat(earlier.warnings()).containsExactly(warning("1003000777"));
    assertThat(flagged(earlier.run())).isEqualTo(1);
  }

  // ---- AC-4 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-4] NPI 1003000333 ACTIVE with effectiveDate 1998-05-01: no warning names it, counts.futureEnrolments is 0, and its record is in the file")
  void nineteenNinetyEightIsNotFlagged() throws Exception {
    TestProviderService.holds(Prov.active("1003000333").effective("1998-05-01"));
    Outcome o = run(DATE);
    assertThat(o.warnings()).isEmpty();
    assertThat(flagged(o.run())).isZero();
    assertThat(o.npis()).containsExactly("1003000333");
  }

  @Test
  @DisplayName("[MAM-78 AC-4] enrolments in 1999, 2000 and 2025 are not flagged either")
  void otherEarlierYearsAreNotFlagged() throws Exception {
    TestProviderService.holds(
        Prov.active("1003000111").effective("1999-12-31"),
        Prov.active("1003000222").effective("2000-01-01"),
        Prov.active("1003000333").effective("2025-12-31"));
    Outcome o = run(DATE);
    assertThat(o.warnings()).isEmpty();
    assertThat(flagged(o.run())).isZero();
  }

  // ---- AC-5 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-5] NPI 1003000888 ACTIVE with effectiveDate 2101-01-01: the log holds 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000888' and counts.futureEnrolments counts it")
  void yearAfter2099IsFlagged() throws Exception {
    TestProviderService.holds(Prov.active("1003000888").effective("2101-01-01"));
    Outcome o = run(DATE);
    assertThat(o.warnings()).containsExactly(warning("1003000888"));
    assertThat(flagged(o.run())).isEqualTo(1);
    assertThat(o.npis()).containsExactly("1003000888");
  }

  // ---- AC-6 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-6] NPI 1003000555 pending (P) with effectiveDate 2027-01-01: no record in the file, no warning line and no count")
  void onlyActiveProvidersAreChecked() throws Exception {
    TestProviderService.holds(Prov.of("1003000555", "P").effective("2027-01-01"));
    TestProviderService.ignoreStatusFilter();
    Outcome o = run(DATE);
    assertThat(o.npis()).isEmpty();
    assertThat(o.warnings()).isEmpty();
    assertThat(flagged(o.run())).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-6] pending, denied, suspended and terminated providers with a later year are not warned about; the ACTIVE one is")
  void nonActiveWithFutureDatesAreIgnored() throws Exception {
    TestProviderService.holds(
        Prov.of("1003000444", "D").effective("2027-01-01"),
        Prov.of("1003000555", "P").effective("2027-01-01"),
        Prov.of("1003000666", "T").effective("2027-01-01"),
        Prov.of("1003000999", "S").effective("2027-01-01"),
        Prov.active("1003000777").effective("2027-01-01"));
    TestProviderService.ignoreStatusFilter();
    Outcome o = run(DATE);
    assertThat(o.warnings()).containsExactly(warning("1003000777"));
    assertThat(flagged(o.run())).isEqualTo(1);
    assertThat(o.npis()).containsExactly("1003000777");
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-1] a provider without an enrolment date in provider-service's answer is reported and not warned about")
  void missingDate() throws Exception {
    TestProviderService.holds(new Prov("1003000111", "M00000111", "A", null));
    Outcome o = run(DATE);
    assertThat(o.warnings()).isEmpty();
    assertThat(o.npis()).containsExactly("1003000111");
  }
}
