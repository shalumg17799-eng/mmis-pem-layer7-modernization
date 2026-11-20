package gov.medicaid.pem.screening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-18: how each source answer becomes a result (D-005, D-016; Q-039, Q-041 defaults). */
class SourceRulesTest {

  static final Instant AT = Instant.parse("2026-11-18T10:00:00Z");

  @Test
  @DisplayName(
      "[MAM-18 AC-2] an ACTIVE licence passes LICBOARD with a detail naming the licence and its expiry")
  void licenceActive() {
    SourceResult r = SourceRules.licence(true, "MD12345", "TX", true, "ACTIVE", "2028-09", AT);
    assertThat(r)
        .isEqualTo(
            new SourceResult(
                Source.LICBOARD, "P", AT, "LICENCE MD12345 TX ACTIVE UNTIL 2028-09", null));
  }

  @Test
  @DisplayName("[MAM-18 AC-3] an EXPIRED licence fails LICBOARD with E202")
  void licenceExpired() {
    SourceResult r = SourceRules.licence(true, "PT99900", "TX", true, "EXPIRED", "2024-01", AT);
    assertThat(r)
        .isEqualTo(
            new SourceResult(
                Source.LICBOARD, "F", AT, "LICENCE PT99900 TX EXPIRED 2024-01", "E202"));
  }

  @Test
  @DisplayName(
      "[MAM-18] an individual licence unknown to the board fails LICBOARD without a code (Q-041 A)")
  void licenceNotFound() {
    assertThat(SourceRules.licence(true, "XX00000", "TX", false, null, null, AT))
        .isEqualTo(new SourceResult(Source.LICBOARD, "F", AT, "LICENCE NOT FOUND", null));
  }

  @Test
  @DisplayName(
      "[MAM-18] a group or facility has no licence check: LICBOARD passes as not applicable (Q-041 A)")
  void licenceNotApplicable() {
    assertThat(SourceRules.licence(false, null, null, false, null, null, AT))
        .isEqualTo(new SourceResult(Source.LICBOARD, "P", AT, "NOT APPLICABLE", null));
  }

  @Test
  @DisplayName("[MAM-18 AC-4] an NPI on the LEIE fails OIGLEIE with E201 and the exclusion")
  void leieExcluded() {
    assertThat(SourceRules.leie(true, "1128(a)(1)", "2023-01-15", AT))
        .isEqualTo(
            new SourceResult(
                Source.OIGLEIE, "F", AT, "EXCLUDED 1128(a)(1) SINCE 2023-01-15", "E201"));
    assertThat(SourceRules.leie(false, null, null, AT))
        .isEqualTo(new SourceResult(Source.OIGLEIE, "P", AT, "NOT EXCLUDED", null));
  }

  @Test
  @DisplayName("[MAM-18 AC-5] a debarred entity fails SAMGOV with E205")
  void samDebarred() {
    assertThat(SourceRules.sam(true, AT))
        .isEqualTo(new SourceResult(Source.SAMGOV, "F", AT, "DEBARRED", "E205"));
    assertThat(SourceRules.sam(false, AT))
        .isEqualTo(new SourceResult(Source.SAMGOV, "P", AT, "NOT DEBARRED", null));
  }

  @Test
  @DisplayName("[MAM-18 AC-6] a deceased provider fails SSADMF with E204")
  void dmfDeceased() {
    assertThat(SourceRules.ssaDmf(true, "2025-08-01", AT))
        .isEqualTo(new SourceResult(Source.SSADMF, "F", AT, "DECEASED 2025-08-01", "E204"));
    assertThat(SourceRules.ssaDmf(false, null, AT))
        .isEqualTo(new SourceResult(Source.SSADMF, "P", AT, "NOT DECEASED", null));
  }

  @Test
  @DisplayName("[MAM-18] a source that cannot be reached gives C without a code (Q-039 A)")
  void unreachable() {
    assertThat(SourceRules.unreachable(Source.SAMGOV, AT))
        .isEqualTo(new SourceResult(Source.SAMGOV, "C", AT, "COULD NOT BE REACHED", null));
  }

  @Test
  @DisplayName("[MAM-18] a detail never exceeds the 60 characters of the stored field")
  void detailLength() {
    SourceResult r =
        SourceRules.licence(
            true,
            "ABCDEFGHIJKLMNO",
            "TX",
            true,
            "SUSPENDED-BY-THE-BOARD-PENDING-REVIEW",
            "2024-01",
            AT);
    assertThat(r.detail().length()).isLessThanOrEqualTo(60);
    assertThat(r.result()).isEqualTo("F");
    assertThat(r.errorCode()).isNull();
  }
}
