package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-21 / MAM-23: verdicts and tiers are written to the provider record, nothing else is touched.
 */
class RecordVerdictsTest {

  final List<String> changes = new ArrayList<>();
  boolean known = true;

  final ProviderChanges port =
      new ProviderChanges() {
        @Override
        public boolean setScreenFlag(String npi, ScreenFlag flag) {
          changes.add(npi + " flag " + flag.code());
          return known;
        }

        @Override
        public boolean setRiskTier(String npi, RiskTier tier) {
          changes.add(npi + " tier " + tier.code());
          return known;
        }
      };

  final RecordVerdicts record = new RecordVerdicts(port);

  @Test
  @DisplayName("[MAM-21 AC-1] a PASS screening sets the screen flag to P")
  void pass() {
    assertThat(record.screeningCompleted("1003000111", "PASS")).isTrue();
    assertThat(changes).containsExactly("1003000111 flag P");
  }

  @Test
  @DisplayName("[MAM-21 AC-2] a FAIL screening sets the screen flag to F")
  void fail() {
    record.screeningCompleted("1003000444", "FAIL");
    assertThat(changes).containsExactly("1003000444 flag F");
  }

  @Test
  @DisplayName("[MAM-21] a verdict other than PASS or FAIL is refused, nothing is written")
  void unknownVerdict() {
    assertThatThrownBy(() -> record.screeningCompleted("1003000111", "MAYBE"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(changes).isEmpty();
  }

  @Test
  @DisplayName("[MAM-23 AC-2] the tier received is stored as it is (L, M, H), never recomputed")
  void tiers() {
    record.riskTierAssigned("1003000111", "L");
    record.riskTierAssigned("1003000222", "H");
    record.riskTierAssigned("1003000888", "M");
    assertThat(changes)
        .containsExactly("1003000111 tier L", "1003000222 tier H", "1003000888 tier M");
  }

  @Test
  @DisplayName("[MAM-23] an event for a provider that is not on file changes nothing and says so")
  void unknownProvider() {
    known = false;
    assertThat(record.riskTierAssigned("1009999999", "L")).isFalse();
    assertThat(record.screeningCompleted("1009999999", "PASS")).isFalse();
  }
}
