package gov.medicaid.pem.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-59 and MAM-58 Lane 3, boundary cases of the notice rules (defaults Q-A7, Q-A8, Q-A10, Q-092):
 * every transition of the provider status from P, A, T, D and S gives its notice or none, an NPI is
 * 10 digits or the rejection gives no notice, and every address is {@code
 * <npi>@providers.pem.local}.
 */
class NoticeRulesMatrixTest {

  private static final List<String> STATUSES = List.of("P", "A", "T", "D", "S");

  @Test
  @DisplayName("[MAM-59 AC-1/AC-2/AC-3] of the 25 transitions only P to A and P to D give a notice")
  void onlyTwoTransitionsGiveANotice() {
    for (String from : STATUSES) {
      for (String to : STATUSES) {
        Optional<String> expected =
            from.equals("P") && to.equals("A")
                ? Optional.of("APPROVED")
                : from.equals("P") && to.equals("D") ? Optional.of("DENIED") : Optional.empty();
        assertThat(NoticeRules.forStatusChange(from, to))
            .as("transition %s to %s", from, to)
            .isEqualTo(expected);
      }
    }
  }

  @ParameterizedTest(name = "[MAM-59 AC-3] a status change from {0} to {1} gives no notice")
  @CsvSource({"P,T", "P,S", "P,R", "P,p", "p,A", "A,P", "D,P", "T,P", "S,D", "'',A", "P,''"})
  void otherChangesGiveNone(String from, String to) {
    assertThat(NoticeRules.forStatusChange(from, to)).isEmpty();
  }

  @Test
  @DisplayName("[MAM-59 AC-3] a status change with a missing status gives no notice")
  void missingStatusGivesNone() {
    assertThat(NoticeRules.forStatusChange(null, "A")).isEmpty();
    assertThat(NoticeRules.forStatusChange("P", null)).isEmpty();
    assertThat(NoticeRules.forStatusChange(null, null)).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-59 AC-4] the NPI {0} is a valid recipient of a rejection")
  @ValueSource(strings = {"1003000222", "0000000000", "9999999999", "1234567890"})
  void tenDigitsGiveRejected(String npi) {
    assertThat(NoticeRules.forRejection(npi)).contains("REJECTED");
  }

  @ParameterizedTest(name = "[MAM-59 AC-5] the NPI ''{0}'' gives no rejection notice")
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "123456789",
        "12345678901",
        "100300022A",
        "ABCDEFGHIJ",
        " 1003000222",
        "1003000222 ",
        "1003000222\n",
        "1003-00022",
        "\uff11\uff10\uff10\uff13\uff10\uff10\uff10\uff12\uff12\uff12"
      })
  void anythingElseGivesNone(String npi) {
    assertThat(NoticeRules.forRejection(npi)).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-58 AC-1/AC-2/AC-3] the sanction action {0} gives the {1} notice")
  @CsvSource({"T,TRM", "S,SUS", "R,RST"})
  void sanctionActions(String action, String template) {
    assertThat(NoticeRules.forSanction(action)).contains(template);
  }

  @ParameterizedTest(name = "[MAM-58 AC-4] the sanction action ''{0}'' gives no notice, not GEN")
  @ValueSource(
      strings = {"X", "TS", "TRM", "SUS", "RST", "GEN", "t", "s", "r", " T", "T ", "A", "D"})
  void otherSanctionActions(String action) {
    assertThat(NoticeRules.forSanction(action)).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-59 AC-1] the notice for NPI {0} is addressed to {1}")
  @CsvSource({
    "1003000111,1003000111@providers.pem.local",
    "1003000444,1003000444@providers.pem.local",
    "0000000001,0000000001@providers.pem.local"
  })
  void addressIsTheNpiAtTheProviderDomain(String npi, String address) {
    assertThat(NoticeRules.recipient(npi)).isEqualTo(address);
  }
}
