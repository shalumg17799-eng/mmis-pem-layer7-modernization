package gov.medicaid.pem.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** MAM-59: which event gives which notice (Q-A7 and Q-023 defaults), and where it is addressed. */
class NoticeRulesTest {

  @Test
  @DisplayName("[MAM-59 AC-1] P to A gives APPROVED")
  void approved() {
    assertThat(NoticeRules.forStatusChange("P", "A")).contains("APPROVED");
  }

  @Test
  @DisplayName("[MAM-59 AC-2] P to D gives DENIED")
  void denied() {
    assertThat(NoticeRules.forStatusChange("P", "D")).contains("DENIED");
  }

  @ParameterizedTest(
      name =
          "[MAM-59 AC-3] {0} to {1} gives no notice (sanction notices come from SanctionApplied)")
  @CsvSource({"A,S", "S,A", "A,T", "A,A", "D,A", "P,P"})
  void none(String from, String to) {
    assertThat(NoticeRules.forStatusChange(from, to)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-4/AC-5] a rejected application gives REJECTED only when it names a 10-digit NPI")
  void rejected() {
    assertThat(NoticeRules.forRejection("1003000222")).contains("REJECTED");
    assertThat(NoticeRules.forRejection(null)).isEmpty();
    assertThat(NoticeRules.forRejection("")).isEmpty();
    assertThat(NoticeRules.forRejection("12345")).isEmpty();
  }

  @Test
  @DisplayName("[MAM-59 AC-1] a notice is addressed to <npi>@providers.pem.local")
  void address() {
    assertThat(NoticeRules.recipient("1003000111")).isEqualTo("1003000111@providers.pem.local");
  }
}
