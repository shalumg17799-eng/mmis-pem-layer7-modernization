package gov.medicaid.pem.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Q-095 (user decision 2026-11-19): an individual applies only under the NPI of their token. */
class OwnNpiRuleTest {

  static final Optional<String> OWN = Optional.of("1003000111");

  @Test
  @DisplayName("[Q-095] an individual (IN) application under the token's own NPI is allowed")
  void own() {
    assertThat(OwnNpiRule.allows("IN", "1003000111", OWN)).isTrue();
  }

  @Test
  @DisplayName("[Q-095] an individual (IN) application under another NPI is refused")
  void other() {
    assertThat(OwnNpiRule.allows("IN", "1003000222", OWN)).isFalse();
  }

  @Test
  @DisplayName(
      "[Q-095] an individual (IN) application from a token without an npi claim is refused")
  void noClaim() {
    assertThat(OwnNpiRule.allows("IN", "1003000111", Optional.empty())).isFalse();
  }

  @Test
  @DisplayName(
      "[Q-095] group, facility and unknown-type applications are not restricted by the token's NPI")
  void notIndividual() {
    assertThat(OwnNpiRule.allows("GR", "1003000444", OWN)).isTrue();
    assertThat(OwnNpiRule.allows("FA", "1003000777", OWN)).isTrue();
    assertThat(OwnNpiRule.allows(null, "1003000222", OWN)).isTrue();
    assertThat(OwnNpiRule.allows("GR", "1003000444", Optional.empty())).isTrue();
  }

  @Test
  @DisplayName(
      "[Q-095] a missing or malformed NPI is not another provider's NPI: it is left to E101 (MAM-46 AC-2)")
  void malformedLeftToE101() {
    assertThat(OwnNpiRule.allows("IN", "", OWN)).isTrue();
    assertThat(OwnNpiRule.allows("IN", "12345", OWN)).isTrue();
    assertThat(OwnNpiRule.allows("IN", null, OWN)).isTrue();
  }
}
