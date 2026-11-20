package gov.medicaid.pem.screening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.domain.SourceRules.Exclusion;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-22: the provider and its owner against the LEIE as one OIGLEIE result (R-PRVSCR01-cbl-00033,
 * D-016, questions C-5 and C-6): E203 for the owner alone, E201 for the provider alone or both.
 */
class SourceRulesOwnerTest {

  static final Instant AT = Instant.parse("2026-01-15T10:00:00Z");
  static final String NPI = "1003000444";
  static final String OWNER = "1990000111";
  static final Exclusion LISTED = new Exclusion(true, "1128(a)(1)", "2023-01-15");
  static final Exclusion LISTED_LATER = new Exclusion(true, "1128(b)(7)", "2024-02-01");
  static final Exclusion CLEAN = new Exclusion(false, "", "");

  @Test
  @DisplayName("[MAM-22 AC-1] only the owner on the LEIE: F with E203, the detail names the owner")
  void ownerOnly() {
    assertThat(SourceRules.leie(NPI, CLEAN, OWNER, LISTED, AT))
        .isEqualTo(
            new SourceResult(
                Source.OIGLEIE,
                "F",
                AT,
                "OWNER 1990000111 EXCLUDED 1128(a)(1) SINCE 2023-01-15",
                "E203"));
  }

  @Test
  @DisplayName("[MAM-22 AC-2] neither the provider nor the owner on the LEIE: P without a code")
  void neither() {
    assertThat(SourceRules.leie(NPI, CLEAN, OWNER, CLEAN, AT))
        .isEqualTo(new SourceResult(Source.OIGLEIE, "P", AT, "NOT EXCLUDED", null));
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-4] the provider and its owner both on the LEIE: one F with E201 naming both")
  void both() {
    assertThat(SourceRules.leie(NPI, LISTED_LATER, OWNER, LISTED, AT))
        .isEqualTo(
            new SourceResult(
                Source.OIGLEIE, "F", AT, "NPI 1003000444 AND OWNER 1990000111 EXCLUDED", "E201"));
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-4] only the provider on the LEIE: E201 with its own exclusion, as without an owner")
  void providerOnly() {
    assertThat(SourceRules.leie(NPI, LISTED, OWNER, CLEAN, AT))
        .isEqualTo(
            new SourceResult(
                Source.OIGLEIE, "F", AT, "EXCLUDED 1128(a)(1) SINCE 2023-01-15", "E201"));
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-3] a provider without an owner is judged on its own NPI: listed gives E201")
  void noOwnerListed() {
    assertThat(SourceRules.leie(NPI, LISTED, null, Exclusion.NONE, AT))
        .isEqualTo(
            new SourceResult(
                Source.OIGLEIE, "F", AT, "EXCLUDED 1128(a)(1) SINCE 2023-01-15", "E201"));
  }

  @Test
  @DisplayName("[MAM-22 AC-3] a provider without an owner and not listed passes")
  void noOwnerClean() {
    assertThat(SourceRules.leie(NPI, CLEAN, null, Exclusion.NONE, AT))
        .isEqualTo(new SourceResult(Source.OIGLEIE, "P", AT, "NOT EXCLUDED", null));
  }

  @ParameterizedTest(
      name =
          "[MAM-22 AC-3] a blank owner NPI <{0}> is no owner: a listing of the owner is not used")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t"})
  void blankOwnerIsIgnored(String blank) {
    assertThat(SourceRules.leie(NPI, CLEAN, blank, LISTED, AT))
        .isEqualTo(new SourceResult(Source.OIGLEIE, "P", AT, "NOT EXCLUDED", null));
  }

  @ParameterizedTest(
      name =
          "[MAM-22 AC-3] a blank owner NPI <{0}> and a listed provider still gives E201 for the provider")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankOwnerListedProvider(String blank) {
    SourceResult r = SourceRules.leie(NPI, LISTED, blank, LISTED, AT);
    assertThat(r.errorCode()).isEqualTo("E201");
    assertThat(r.detail()).isEqualTo("EXCLUDED 1128(a)(1) SINCE 2023-01-15");
  }

  @Test
  @DisplayName("[MAM-22 AC-1] the result is the OIGLEIE source and carries the verdict time given")
  void sourceAndTime() {
    SourceResult r = SourceRules.leie(NPI, CLEAN, OWNER, LISTED, AT);
    assertThat(r.source()).isEqualTo(Source.OIGLEIE);
    assertThat(r.verdictAt()).isEqualTo(AT);
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-1] the detail of an owner exclusion is cut to the 60 characters of the field")
  void ownerDetailCut() {
    Exclusion longType = new Exclusion(true, "X".repeat(40), "2023-01-15");
    SourceResult r = SourceRules.leie(NPI, CLEAN, OWNER, longType, AT);
    assertThat(r.detail()).hasSize(60).startsWith("OWNER 1990000111 EXCLUDED XXXX");
    assertThat(r.errorCode()).isEqualTo("E203");
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-4] the detail of the exclusion of the provider alone is cut to 60 characters")
  void ownDetailCut() {
    Exclusion longType = new Exclusion(true, "Y".repeat(80), "2023-01-15");
    SourceResult r = SourceRules.leie(NPI, longType, null, Exclusion.NONE, AT);
    assertThat(r.detail()).hasSize(60).startsWith("EXCLUDED YYYY");
  }

  @Test
  @DisplayName("[MAM-22 AC-2] Exclusion.NONE is a clean answer without type or date")
  void none() {
    assertThat(Exclusion.NONE.excluded()).isFalse();
    assertThat(Exclusion.NONE.type()).isNull();
    assertThat(Exclusion.NONE.since()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-1] the rule with an owner is the same answer as the rule without one when there is no owner")
  void sameAsWithoutOwner() {
    assertThat(SourceRules.leie(NPI, LISTED, null, Exclusion.NONE, AT))
        .isEqualTo(SourceRules.leie(true, "1128(a)(1)", "2023-01-15", AT));
    assertThat(SourceRules.leie(NPI, CLEAN, null, Exclusion.NONE, AT))
        .isEqualTo(SourceRules.leie(false, null, null, AT));
  }
}
