package gov.medicaid.pem.directory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-67 AC-5 and MAM-65 AC-2/AC-3: the name that DIROUT and the FHIR resources show is composed
 * from the name fields (D-028, R-PRVDIR01-ADD-02), never the legacy first 60 bytes of PRV-VARIANT
 * (last name 30 + first name 20 + 10 characters of the licence number).
 */
class DirectoryNameCompositionTest {

  @Test
  @DisplayName("[MAM-67 AC-5] an individual is first name, one space, last name: MARIA RIVERA")
  void individual() {
    assertThat(DirectoryRules.displayName("IN", "MARIA", "RIVERA", null)).isEqualTo("MARIA RIVERA");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] the padding of the legacy 30-byte and 20-byte name fields is dropped")
  void legacyPaddingDropped() {
    String lastName = "RIVERA" + " ".repeat(24);
    String firstName = "MARIA" + " ".repeat(15);
    assertThat(DirectoryRules.displayName("IN", firstName, lastName, null))
        .isEqualTo("MARIA RIVERA");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] the licence number never reaches the name")
  void licenceNotInName() {
    assertThat(DirectoryRules.displayName("IN", "MARIA", "RIVERA", null)).doesNotContain("MD12345");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] a facility is its legal name, never the DBA name")
  void facilityLegalName() {
    assertThat(DirectoryRules.displayName("FA", null, null, "SUNSET HOSPICE INC"))
        .isEqualTo("SUNSET HOSPICE INC");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] a group is its legal name")
  void groupLegalName() {
    assertThat(DirectoryRules.displayName("GR", null, null, "EXCLUDED CLINIC LLC"))
        .isEqualTo("EXCLUDED CLINIC LLC");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-5] the name fields of the other view are ignored: an organisation's person fields and an individual's legal name")
  void otherViewIgnored() {
    assertThat(DirectoryRules.displayName("FA", "MARIA", "RIVERA", "SUNSET HOSPICE INC"))
        .isEqualTo("SUNSET HOSPICE INC");
    assertThat(DirectoryRules.displayName("IN", "MARIA", "RIVERA", "SUNSET HOSPICE INC"))
        .isEqualTo("MARIA RIVERA");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] inner runs of spaces are collapsed to one")
  void innerSpacesCollapsed() {
    assertThat(DirectoryRules.displayName("IN", "MARIA   ELENA", "DE   LA CRUZ", null))
        .isEqualTo("MARIA ELENA DE LA CRUZ");
    assertThat(DirectoryRules.displayName("GR", null, null, "SUNSET   HOSPICE  INC"))
        .isEqualTo("SUNSET HOSPICE INC");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] tabs and other white space between words count as spaces")
  void otherWhiteSpace() {
    assertThat(DirectoryRules.displayName("IN", "MARIA\t", "\tRIVERA", null))
        .isEqualTo("MARIA RIVERA");
  }

  @ParameterizedTest(
      name = "[MAM-67 AC-5] an individual with first name <{0}> is the last name only")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void individualWithoutFirstName(String first) {
    assertThat(DirectoryRules.displayName("IN", first, "RIVERA", null)).isEqualTo("RIVERA");
  }

  @ParameterizedTest(
      name = "[MAM-67 AC-5] an individual with last name <{0}> is the first name only")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void individualWithoutLastName(String last) {
    assertThat(DirectoryRules.displayName("IN", "MARIA", last, null)).isEqualTo("MARIA");
  }

  @ParameterizedTest(name = "[MAM-67 AC-5] an organisation with legal name <{0}> has an empty name")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void organisationWithoutLegalName(String legal) {
    assertThat(DirectoryRules.displayName("FA", null, null, legal)).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-5] a name of exactly 60 characters is kept whole")
  void sixtyKept() {
    String legal = "L".repeat(60);
    assertThat(DirectoryRules.displayName("FA", null, null, legal)).isEqualTo(legal);
  }

  @Test
  @DisplayName("[MAM-67 AC-5] a name of 61 characters is cut to 60")
  void sixtyOneCut() {
    assertThat(DirectoryRules.displayName("FA", null, null, "L".repeat(61)))
        .isEqualTo("L".repeat(60));
  }

  @Test
  @DisplayName("[MAM-67 AC-5] a name cut after a space leaves no trailing space")
  void cutAtSpaceTrimmed() {
    String legal = "A".repeat(59) + " B";
    assertThat(DirectoryRules.displayName("FA", null, null, legal)).isEqualTo("A".repeat(59));
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-5] a long individual name is cut as a whole, after first + last are joined")
  void longIndividualCut() {
    String name = DirectoryRules.displayName("IN", "F".repeat(30), "L".repeat(40), null);
    assertThat(name).hasSize(60).startsWith("F".repeat(30) + " ");
  }

  @ParameterizedTest(
      name = "[MAM-67 AC-5] provider type <{0}> that is not IN is shown by the legal name")
  @ValueSource(strings = {"GR", "FA", "XX", "in", ""})
  void typeDecidesTheView(String type) {
    assertThat(DirectoryRules.displayName(type, "MARIA", "RIVERA", "SUNSET HOSPICE INC"))
        .isEqualTo("SUNSET HOSPICE INC");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-3] only status A is published; a lower-case a, an empty status or none is not")
  void publishedIsExactlyA() {
    assertThat(DirectoryRules.published("A")).isTrue();
    assertThat(DirectoryRules.published("a")).isFalse();
    assertThat(DirectoryRules.published("")).isFalse();
    assertThat(DirectoryRules.published(null)).isFalse();
  }
}
