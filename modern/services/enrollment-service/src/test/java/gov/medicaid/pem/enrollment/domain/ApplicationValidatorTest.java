package gov.medicaid.pem.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-46: PRVENR01 VALIDATE-APPLICATION (NPI 128-133, type 134-139, taxonomy 141-145) and the
 * duplicate branch.
 */
class ApplicationValidatorTest {

  static final ProviderTypeMapping MAPPING =
      ProviderTypeMapping.of(Map.of("IND", "IN", "GRP", "GR", "HOSP", "FA"));

  static List<String> errors(Validation v) {
    return v.errors().stream().map(e -> e.code().name() + "/" + e.field()).toList();
  }

  static Validation check(String npi, String type, String taxonomy, boolean duplicate) {
    return ApplicationValidator.validate(npi, type, taxonomy, MAPPING, duplicate);
  }

  @Test
  @DisplayName("[MAM-46 AC-1] a complete application with type IN has no errors and keeps type IN")
  void valid() {
    Validation v = check("1003000111", "IN", "207Q00000X", false);
    assertThat(v.errors()).isEmpty();
    assertThat(v.valid()).isTrue();
    assertThat(v.providerType()).isEqualTo("IN");
  }

  @ParameterizedTest(name = "[MAM-46 AC-2] npi \"{0}\" fails E101 only")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "12345", "10030001111", "10030001AB", " 1003000111"})
  void e101(String npi) {
    Validation v = check(npi, "IN", "207Q00000X", false);
    assertThat(errors(v)).containsExactly("E101/npi");
    assertThat(v.valid()).isFalse();
  }

  @Test
  @DisplayName("[MAM-46 AC-2] an invalid NPI is never also reported as a duplicate")
  void e101NotDuplicate() {
    assertThat(errors(check("", "IN", "207Q00000X", true))).containsExactly("E101/npi");
  }

  @ParameterizedTest(name = "[MAM-46 AC-3] provider type \"{0}\" fails E102 only")
  @NullAndEmptySource
  @ValueSource(strings = {"XX", "ZZZ", "in", "AMB", "  "})
  void e102(String type) {
    Validation v = check("1003000222", type, "207Q00000X", false);
    assertThat(errors(v)).containsExactly("E102/providerType");
    assertThat(v.valid()).isFalse();
  }

  @ParameterizedTest(name = "[MAM-46 AC-3] provider type {0} is accepted as itself")
  @ValueSource(strings = {"IN", "GR", "FA"})
  void legacyTypes(String type) {
    Validation v = check("1003000444", type, "193200000X", false);
    assertThat(v.errors()).isEmpty();
    assertThat(v.providerType()).isEqualTo(type);
  }

  @Test
  @DisplayName("[MAM-46 AC-7] feed codes IND, GRP and HOSP are mapped to IN, GR and FA")
  void mapped() {
    assertThat(check("1003000111", "IND", "207Q00000X", false).providerType()).isEqualTo("IN");
    assertThat(check("1003000444", "GRP", "193200000X", false).providerType()).isEqualTo("GR");
    assertThat(check("1003000777", "HOSP", "282N00000X", false).providerType()).isEqualTo("FA");
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-7] the mapping table answers legacy codes as themselves and nothing for unknown codes")
  void mappingTable() {
    assertThat(MAPPING.toLegacy("IND")).contains("IN");
    assertThat(MAPPING.toLegacy("FA")).contains("FA");
    assertThat(MAPPING.toLegacy("ZZZ")).isEmpty();
    assertThat(MAPPING.toLegacy(null)).isEmpty();
    assertThat(ProviderTypeMapping.of(Map.of()).toLegacy("IND")).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-46 AC-4] taxonomy \"{0}\" fails E104 only")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void e104(String taxonomy) {
    Validation v = check("1003000111", "IN", taxonomy, false);
    assertThat(errors(v)).containsExactly("E104/taxonomy");
    assertThat(v.valid()).isFalse();
  }

  @Test
  @DisplayName("[MAM-46 AC-5] a valid NPI that already has a VALIDATED application fails E103 only")
  void e103() {
    Validation v = check("1003000111", "IN", "207Q00000X", true);
    assertThat(errors(v)).containsExactly("E103/npi");
    assertThat(v.valid()).isFalse();
  }

  @Test
  @DisplayName("[MAM-46 AC-6] type XX and an empty taxonomy give E102 and E104, each once")
  void several() {
    assertThat(errors(check("1003000333", "XX", "", false)))
        .containsExactlyInAnyOrder("E102/providerType", "E104/taxonomy");
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-6] every check runs: missing NPI, bad type and missing taxonomy give three errors")
  void allThree() {
    Validation v = check(null, "XX", null, false);
    assertThat(errors(v))
        .containsExactlyInAnyOrder("E101/npi", "E102/providerType", "E104/taxonomy");
    assertThat(v.providerType()).isNull();
  }
}
