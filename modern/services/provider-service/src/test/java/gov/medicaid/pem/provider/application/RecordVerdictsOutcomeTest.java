package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import gov.medicaid.pem.provider.LogCapture;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-21 / MAM-23: what a verdict or a tier reports back. The answer says whether the provider was
 * on file (true) or not (false), for both ways of naming the producer, and a provider that is not
 * on file is logged once as a warning with the NPI, while one that is on file logs no warning.
 */
class RecordVerdictsOutcomeTest {

  boolean known = true;

  final ProviderChanges port =
      new ProviderChanges() {
        @Override
        public boolean setScreenFlag(String npi, ScreenFlag flag) {
          return known;
        }

        @Override
        public boolean setRiskTier(String npi, RiskTier tier) {
          return known;
        }
      };

  final RecordVerdicts record = new RecordVerdicts(port);

  private List<LogCapture.Line> warnings(LogCapture log) {
    return log.lines().stream()
        .filter(l -> l.logger().endsWith("RecordVerdicts") && l.level() == Level.WARN)
        .toList();
  }

  @Test
  @DisplayName(
      "[MAM-21 AC-1] a verdict for a provider on file is reported as applied, with or without a named producer")
  void verdictAppliedIsTrue() {
    assertThat(record.screeningCompleted("1003000111", "PASS")).isTrue();
    assertThat(record.screeningCompleted("1003000111", "FAIL", "screening-service")).isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-23 AC-1] a tier for a provider on file is reported as applied, with or without a named producer")
  void tierAppliedIsTrue() {
    assertThat(record.riskTierAssigned("1003000111", "L")).isTrue();
    assertThat(record.riskTierAssigned("1003000111", "H", "risk-service")).isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-21 AC-1] a verdict for a provider that is not on file is reported as not applied, with or without a named producer")
  void verdictNotAppliedIsFalse() {
    known = false;

    assertThat(record.screeningCompleted("1009999999", "PASS")).isFalse();
    assertThat(record.screeningCompleted("1009999999", "PASS", "screening-service")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-23 AC-1] a tier for a provider that is not on file is reported as not applied, with or without a named producer")
  void tierNotAppliedIsFalse() {
    known = false;

    assertThat(record.riskTierAssigned("1009999999", "M")).isFalse();
    assertThat(record.riskTierAssigned("1009999999", "M", "risk-service")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-21 AC-1] a verdict or a tier for a provider that is not on file is logged once each as a warning with the NPI and what was not applied")
  void notAppliedIsWarned() {
    known = false;
    try (LogCapture log = LogCapture.start()) {
      record.screeningCompleted("1009999999", "PASS");
      record.riskTierAssigned("1009999998", "L");

      List<LogCapture.Line> warned = warnings(log);
      assertThat(warned).hasSize(2);
      assertThat(warned.get(0).message())
          .contains("screening verdict")
          .contains("1009999999")
          .contains("not applied");
      assertThat(warned.get(1).message())
          .contains("risk tier")
          .contains("1009999998")
          .contains("not applied");
    }
  }

  @Test
  @DisplayName("[MAM-23 AC-1] a verdict or a tier that is applied logs no warning")
  void appliedIsNotWarned() {
    try (LogCapture log = LogCapture.start()) {
      record.screeningCompleted("1003000111", "PASS");
      record.riskTierAssigned("1003000111", "L");

      assertThat(warnings(log)).isEmpty();
    }
  }
}
