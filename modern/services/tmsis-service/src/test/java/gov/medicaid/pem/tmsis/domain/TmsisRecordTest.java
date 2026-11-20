package gov.medicaid.pem.tmsis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-71 AC-1 and AC-2: only ACTIVE providers are reported (R-PRVTMS01-cbl-00004, -00030, -00032,
 * R-PRVTMS01-ADD-04, Q-007, D-006); the record carries the Medicaid ID and NPI unchanged, the
 * status 'A' and the period of the run (R-PRVTMS01-cbl-00007 to -00009, -00038 to -00040).
 */
class TmsisRecordTest {

  @Test
  @DisplayName(
      "[MAM-71 AC-1] an ACTIVE provider gives a record with its Medicaid ID, NPI, status A and the period")
  void activeProviderIsReported() {
    Optional<TmsisRecord> r = TmsisRecord.of("M00000111", "1003000111", "A", "26090");
    assertThat(r).isPresent();
    assertThat(r.get().medicaidId()).isEqualTo("M00000111");
    assertThat(r.get().npi()).isEqualTo("1003000111");
    assertThat(r.get().status()).isEqualTo("A");
    assertThat(r.get().periodYyddd()).isEqualTo("26090");
  }

  @ParameterizedTest(
      name = "[MAM-71 AC-1] a provider in status {0} is not reported (P pending, T terminated, D denied, S suspended)")
  @ValueSource(strings = {"P", "T", "D", "S"})
  void everyOtherLegacyStatusIsSkipped(String status) {
    assertThat(TmsisRecord.of("M00000444", "1003000444", status, "26090")).isEmpty();
  }

  @ParameterizedTest(
      name = "[MAM-71 AC-1] the status <{0}> is not the status A, so the provider is not reported")
  @ValueSource(strings = {"a", "AA", "", " ", " A", "A ", "ACTIVE", "U", "X", "1"})
  void onlyTheExactStatusLetterCounts(String status) {
    assertThat(TmsisRecord.of("M00000444", "1003000444", status, "26090")).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-71 AC-1] a provider with no status is not reported")
  @NullSource
  void missingStatusIsSkipped(String status) {
    assertThat(TmsisRecord.of("M00000444", "1003000444", status, "26090")).isEmpty();
  }

  @Test
  @DisplayName("[MAM-71 AC-2] the status of a record is always the letter A")
  void statusIsAlwaysA() {
    assertThat(TmsisRecord.ACTIVE).isEqualTo("A");
    assertThat(TmsisRecord.of("M00000111", "1003000111", "A", "26090").orElseThrow().status())
        .isEqualTo("A");
  }

  @Test
  @DisplayName("[MAM-71 AC-2] the Medicaid ID and the NPI are copied unchanged, spaces and all")
  void idsAreCopiedUnchanged() {
    TmsisRecord r = TmsisRecord.of(" M0000111", "1003000111", "A", "26090").orElseThrow();
    assertThat(r.medicaidId()).isEqualTo(" M0000111");
    assertThat(r.npi()).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("[MAM-71 AC-3] the period given is the period of the record, whatever it is")
  void periodIsTheOneGiven() {
    assertThat(TmsisRecord.of("M00000111", "1003000111", "A", "28366").orElseThrow().periodYyddd())
        .isEqualTo("28366");
  }
}
