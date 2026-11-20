package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProviderCodesTest {

  @ParameterizedTest(name = "[MAM-34 AC-1] status {0} is {1}")
  @CsvSource({"P,PENDING", "A,ACTIVE", "T,TERMINATED", "D,DENIED", "S,SUSPENDED"})
  @DisplayName(
      "[MAM-34 AC-1] enrolment status codes P, A, T, D, S (PRVRECRD 88-levels; S added by D-006)")
  void enrollStatusCodes(String code, EnrollStatus expected) {
    assertThat(EnrollStatus.fromCode(code)).isEqualTo(expected);
    assertThat(expected.code()).isEqualTo(code);
  }

  @ParameterizedTest(name = "[MAM-34 AC-1] screen flag {0} is {1}")
  @CsvSource({"U,UNSCREENED", "P,PASS", "F,FAIL"})
  @DisplayName("[MAM-34 AC-1] screen flag codes U, P, F")
  void screenFlagCodes(String code, ScreenFlag expected) {
    assertThat(ScreenFlag.fromCode(code)).isEqualTo(expected);
    assertThat(expected.code()).isEqualTo(code);
  }

  @ParameterizedTest(name = "[MAM-34 AC-1] risk tier {0} is {1}")
  @CsvSource({"L,LIMITED", "M,MODERATE", "H,HIGH"})
  @DisplayName("[MAM-34 AC-1] risk tier codes L, M, H")
  void riskTierCodes(String code, RiskTier expected) {
    assertThat(RiskTier.fromCode(code)).isEqualTo(expected);
    assertThat(expected.code()).isEqualTo(code);
  }

  @ParameterizedTest(name = "[MAM-34 AC-1] provider type {0} is {1}")
  @CsvSource({"IN,INDIVIDUAL", "GR,GROUP", "FA,FACILITY"})
  @DisplayName("[MAM-34 AC-1] provider type codes IN, GR, FA (D-008)")
  void providerTypeCodes(String code, ProviderType expected) {
    assertThat(ProviderType.fromCode(code)).isEqualTo(expected);
    assertThat(expected.code()).isEqualTo(code);
  }

  @Test
  @DisplayName("[MAM-34 AC-1] an unknown code is rejected, not guessed")
  void unknownCodes() {
    assertThatThrownBy(() -> EnrollStatus.fromCode("X"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScreenFlag.fromCode("X")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskTier.fromCode("X")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProviderType.fromCode("XX"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(RiskTier.fromNullableCode(null)).isNull();
    assertThat(RiskTier.fromNullableCode(" ")).isNull();
  }
}
