package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-33 AC-1 to AC-3: each sanction action names the status it gives (PRVSAN01 APPLY-SANCTION: T,
 * S and R; D-006 makes S its own status, not DENIED).
 */
class StatusActionResultTest {

  @ParameterizedTest(name = "[MAM-33 AC-1/AC-2/AC-3] {0} gives the status {1}")
  @CsvSource({"TERMINATE,TERMINATED", "SUSPEND,SUSPENDED", "REINSTATE,ACTIVE"})
  void everyActionNamesItsStatus(StatusAction action, EnrollStatus status) {
    assertThat(action.resultingStatus()).isEqualTo(status);
  }
}
