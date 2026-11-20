package gov.medicaid.pem.tmsis.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import gov.medicaid.pem.tmsis.LogCapture;
import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-78 and MAM-71 AC-1: one reported provider of a run. Only ACTIVE providers become items, so
 * only they are checked (R-PRVTMS01-ADD-04); a provider whose enrolment year is later than the
 * business year is logged once with the legacy DISPLAY text and still reported unchanged
 * (R-PRVTMS01-cbl-00006, -00036, -00037).
 */
class ExtractItemTest {

  static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-03-31");
  static final String PERIOD = "26090";

  static ProviderRow provider(String npi, String status, String effective) {
    return new ProviderRow(
        npi,
        "M00000" + npi.substring(7),
        status,
        effective == null ? null : LocalDate.parse(effective));
  }

  /** The items of the provider and the warning lines logged while it was evaluated. */
  record Result(Optional<ExtractItem> item, List<String> warnings, List<LogCapture.Line> all) {}

  static Result evaluate(ProviderRow p) {
    try (LogCapture log = LogCapture.open()) {
      Optional<ExtractItem> item = ExtractItem.of(p, BUSINESS_DATE, PERIOD);
      return new Result(item, log.messages(Level.WARN), log.lines());
    }
  }

  // ---- AC-1: the warning names the provider -----------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-1] a later enrolment year logs exactly one WARN line 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777'")
  void warningNamesTheProvider() {
    Result r = evaluate(provider("1003000777", "A", "2027-01-15"));
    assertThat(r.warnings()).containsExactly("PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777");
  }

  @Test
  @DisplayName("[MAM-78 AC-1] the warning is logged by the extract item, at level WARN only")
  void warningLoggerAndLevel() {
    Result r = evaluate(provider("1003000777", "A", "2027-01-15"));
    assertThat(r.all()).hasSize(1);
    assertThat(r.all().get(0).level()).isEqualTo(Level.WARN);
    assertThat(r.all().get(0).logger()).isEqualTo(ExtractItem.class.getName());
  }

  @Test
  @DisplayName("[MAM-78 AC-1] the item of a flagged provider is marked as a future enrolment")
  void itemIsMarked() {
    ExtractItem item = evaluate(provider("1003000777", "A", "2027-01-15")).item().orElseThrow();
    assertThat(item.futureEnrolment()).isTrue();
  }

  // ---- AC-2: the flagged provider is still reported unchanged -----------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-2] a flagged provider still has its record, with status A and the period 26090, as an unflagged one")
  void flaggedProviderStillReported() {
    ExtractItem flagged = evaluate(provider("1003000777", "A", "2027-01-15")).item().orElseThrow();
    ExtractItem normal = evaluate(provider("1003000777", "A", "2026-01-15")).item().orElseThrow();
    assertThat(flagged.record()).isEqualTo(normal.record());
    assertThat(flagged.record()).isEqualTo(new TmsisRecord("M00000777", "1003000777", "A", PERIOD));
  }

  // ---- AC-3 to AC-5: which years are flagged ----------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-78 AC-3] effective {0} against the business date 2026-03-31 is not flagged and not logged")
  @ValueSource(strings = {"2026-03-31", "2026-12-31", "2026-01-01", "2025-12-31"})
  void notLaterYearIsNotFlagged(String effective) {
    Result r = evaluate(provider("1003000333", "A", effective));
    assertThat(r.item().orElseThrow().futureEnrolment()).isFalse();
    assertThat(r.all()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-78 AC-3] the first day of the next year, 2027-01-01, is flagged")
  void nextYearIsFlagged() {
    Result r = evaluate(provider("1003000777", "A", "2027-01-01"));
    assertThat(r.item().orElseThrow().futureEnrolment()).isTrue();
    assertThat(r.warnings()).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-4] an enrolment in 1998 is not flagged and not logged (the legacy two-digit test flagged it); it is reported")
  void ninetyEightIsNotFlagged() {
    Result r = evaluate(provider("1003000333", "A", "1998-05-01"));
    assertThat(r.item()).isPresent();
    assertThat(r.item().get().futureEnrolment()).isFalse();
    assertThat(r.all()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-5] an enrolment in 2101 is flagged and logged with its NPI (the legacy two-digit test could not)")
  void year2101IsFlagged() {
    Result r = evaluate(provider("1003000888", "A", "2101-01-01"));
    assertThat(r.item().orElseThrow().futureEnrolment()).isTrue();
    assertThat(r.warnings()).containsExactly("PRVTMS01: FUTURE ENROLLMENT? NPI 1003000888");
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-3] the business date's year is the reference: the same enrolment is flagged for one business year and not for another")
  void referenceYearIsTheBusinessYear() {
    ProviderRow p = provider("1003000777", "A", "2027-02-01");
    try (LogCapture log = LogCapture.open()) {
      assertThat(
              ExtractItem.of(p, LocalDate.parse("2026-12-31"), "26365")
                  .orElseThrow()
                  .futureEnrolment())
          .isTrue();
      assertThat(
              ExtractItem.of(p, LocalDate.parse("2027-01-01"), "27001")
                  .orElseThrow()
                  .futureEnrolment())
          .isFalse();
      assertThat(log.messages(Level.WARN)).hasSize(1);
    }
  }

  @Test
  @DisplayName("[MAM-78 AC-1] a provider without an enrolment date is reported and not flagged")
  void noEnrolmentDate() {
    Result r = evaluate(provider("1003000111", "A", null));
    assertThat(r.item()).isPresent();
    assertThat(r.item().get().futureEnrolment()).isFalse();
    assertThat(r.all()).isEmpty();
  }

  // ---- AC-6: only ACTIVE providers are checked --------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-78 AC-6] a provider in status {0} with enrolment 2027-01-01 has no item, no warning line and no count")
  @ValueSource(strings = {"P", "T", "D", "S"})
  void nonActiveIsNotChecked(String status) {
    Result r = evaluate(provider("1003000555", status, "2027-01-01"));
    assertThat(r.item()).isEmpty();
    assertThat(r.all()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-71 AC-1] an ACTIVE provider's item holds the record the file is written from")
  void activeItemRecord() {
    ExtractItem item = evaluate(provider("1003000111", "A", "2026-03-31")).item().orElseThrow();
    assertThat(item.record().npi()).isEqualTo("1003000111");
    assertThat(item.record().medicaidId()).isEqualTo("M00000111");
    assertThat(item.record().status()).isEqualTo("A");
    assertThat(item.record().periodYyddd()).isEqualTo(PERIOD);
  }
}
