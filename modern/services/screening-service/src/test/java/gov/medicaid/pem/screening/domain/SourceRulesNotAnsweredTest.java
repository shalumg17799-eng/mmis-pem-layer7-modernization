package gov.medicaid.pem.screening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-20: a source without an answer is C without an error code (D-024, D-016, question C-1 A), the
 * licence rules for unknown licences and organisations (question C-3 A), and the detail length.
 */
class SourceRulesNotAnsweredTest {

  static final Instant AT = Instant.parse("2026-01-15T10:00:00Z");

  @ParameterizedTest(
      name = "[MAM-20 AC-1] {0} that cannot be reached is C, no code, COULD NOT BE REACHED")
  @EnumSource(Source.class)
  void unreachable(Source source) {
    assertThat(SourceRules.unreachable(source, AT))
        .isEqualTo(new SourceResult(source, "C", AT, "COULD NOT BE REACHED", null));
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-2] {0} that does not answer in time is C, no code, DID NOT ANSWER IN TIME")
  @EnumSource(Source.class)
  void timedOut(Source source) {
    assertThat(SourceRules.timedOut(source, AT))
        .isEqualTo(new SourceResult(source, "C", AT, "DID NOT ANSWER IN TIME", null));
  }

  @Test
  @DisplayName("[MAM-20 AC-1] the two details of a source without an answer differ")
  void detailsDiffer() {
    assertThat(SourceRules.timedOut(Source.SAMGOV, AT).detail())
        .isNotEqualTo(SourceRules.unreachable(Source.SAMGOV, AT).detail());
  }

  @Test
  @DisplayName("[MAM-20 AC-1] one source without an answer fails the screening, the others passing")
  void oneNotAnsweredFails() {
    List<SourceResult> results =
        List.of(
            new SourceResult(Source.LICBOARD, "P", AT, "OK", null),
            new SourceResult(Source.OIGLEIE, "P", AT, "OK", null),
            SourceRules.unreachable(Source.SAMGOV, AT),
            new SourceResult(Source.SSADMF, "P", AT, "OK", null));
    assertThat(Verdict.of(results)).isEqualTo("FAIL");
  }

  @Test
  @DisplayName("[MAM-20 AC-3] an individual licence the board does not know is F without a code")
  void licenceUnknown() {
    assertThat(SourceRules.licence(true, "XX00000", "TX", false, null, null, AT))
        .isEqualTo(new SourceResult(Source.LICBOARD, "F", AT, "LICENCE NOT FOUND", null));
  }

  @Test
  @DisplayName("[MAM-20 AC-3] an individual without a licence number is F LICENCE NOT FOUND too")
  void licenceMissing() {
    SourceResult r = SourceRules.licence(true, null, null, false, null, null, AT);
    assertThat(r.result()).isEqualTo("F");
    assertThat(r.errorCode()).isNull();
    assertThat(r.detail()).isEqualTo("LICENCE NOT FOUND");
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-4] licence check of an organisation: found={0} gives P NOT APPLICABLE")
  @ValueSource(booleans = {true, false})
  void organisationNeverChecked(boolean found) {
    assertThat(SourceRules.licence(false, "GR11100", "TX", found, "EXPIRED", "2024-01", AT))
        .isEqualTo(new SourceResult(Source.LICBOARD, "P", AT, "NOT APPLICABLE", null));
  }

  @ParameterizedTest(name = "[MAM-20 AC-3] a licence with status {0} fails without a code")
  @ValueSource(strings = {"SUSPENDED", "REVOKED", "PENDING", "active"})
  void otherStatusFailsWithoutCode(String status) {
    SourceResult r = SourceRules.licence(true, "MD12345", "TX", true, status, "2028-09", AT);
    assertThat(r.result()).isEqualTo("F");
    assertThat(r.errorCode()).isNull();
    assertThat(r.detail()).isEqualTo("LICENCE MD12345 TX " + status);
  }

  @Test
  @DisplayName("[MAM-20 AC-3] an EXPIRED licence has E202 and its expiry in the detail")
  void expired() {
    SourceResult r = SourceRules.licence(true, "PT99900", "TX", true, "EXPIRED", "2024-01", AT);
    assertThat(r.errorCode()).isEqualTo("E202");
    assertThat(r.detail()).isEqualTo("LICENCE PT99900 TX EXPIRED 2024-01");
  }

  @Test
  @DisplayName("[MAM-20 AC-3] the detail of a licence is cut at exactly 60 characters, not 59 or 61")
  void cutBoundary() {
    assertThat(SourceRules.cut("A".repeat(59))).hasSize(59);
    assertThat(SourceRules.cut("A".repeat(60))).hasSize(60);
    assertThat(SourceRules.cut("A".repeat(61))).hasSize(60).isEqualTo("A".repeat(60));
    assertThat(SourceRules.cut("")).isEmpty();
    assertThat(SourceRules.DETAIL_MAX).isEqualTo(60);
  }

  @Test
  @DisplayName("[MAM-20 AC-3] a licence detail of exactly 60 characters is kept whole")
  void licenceDetailExactly60() {
    // "LICENCE " (8) + 15 + " TX " (4) + status 33 = 60
    SourceResult r =
        SourceRules.licence(true, "ABCDEFGHIJKLMNO", "TX", true, "S".repeat(33), "2024-01", AT);
    assertThat(r.detail()).hasSize(60);
    assertThat(r.detail()).endsWith("S".repeat(33));
  }
}
