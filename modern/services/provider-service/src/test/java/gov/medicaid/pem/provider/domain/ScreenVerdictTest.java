package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-21: the screening verdict maps to the screen flag (PASS -> P, FAIL -> F). */
class ScreenVerdictTest {

  @Test
  @DisplayName("[MAM-21 AC-1] PASS gives screen flag P")
  void pass() {
    assertThat(ScreenFlag.fromVerdict("PASS")).isEqualTo(ScreenFlag.PASS);
  }

  @Test
  @DisplayName("[MAM-21 AC-2] FAIL gives screen flag F")
  void fail() {
    assertThat(ScreenFlag.fromVerdict("FAIL")).isEqualTo(ScreenFlag.FAIL);
  }

  @Test
  @DisplayName("[MAM-21] any other verdict is not a screen flag")
  void other() {
    assertThatThrownBy(() -> ScreenFlag.fromVerdict("U"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScreenFlag.fromVerdict(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
