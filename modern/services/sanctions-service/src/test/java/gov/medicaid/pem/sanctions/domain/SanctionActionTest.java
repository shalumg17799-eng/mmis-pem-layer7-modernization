package gov.medicaid.pem.sanctions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-52: the SANCIN action codes (PRVSAN01 106-112, D-006, D-026). */
class SanctionActionTest {

  @Test
  @DisplayName("[MAM-52 AC-1] T asks provider-service to TERMINATE and results in T")
  void terminate() {
    assertThat(SanctionAction.fromCode("T")).contains(SanctionAction.T);
    assertThat(SanctionAction.T.statusChange()).isEqualTo("TERMINATE");
    assertThat(SanctionAction.T.resultingStatus()).isEqualTo("T");
  }

  @Test
  @DisplayName("[MAM-52 AC-2] S asks provider-service to SUSPEND and results in S, not D")
  void suspend() {
    assertThat(SanctionAction.fromCode("S")).contains(SanctionAction.S);
    assertThat(SanctionAction.S.statusChange()).isEqualTo("SUSPEND");
    assertThat(SanctionAction.S.resultingStatus()).isEqualTo("S").isNotEqualTo("D");
  }

  @Test
  @DisplayName("[MAM-52 AC-3] R asks provider-service to REINSTATE and results in A")
  void reinstate() {
    assertThat(SanctionAction.fromCode("R")).contains(SanctionAction.R);
    assertThat(SanctionAction.R.statusChange()).isEqualTo("REINSTATE");
    assertThat(SanctionAction.R.resultingStatus()).isEqualTo("A");
  }

  @Test
  @DisplayName("[MAM-52 AC-5] any other action code is unknown")
  void unknown() {
    assertThat(SanctionAction.fromCode("X")).isEmpty();
    assertThat(SanctionAction.fromCode("")).isEmpty();
    assertThat(SanctionAction.fromCode(null)).isEmpty();
    assertThat(SanctionAction.fromCode("t")).isEmpty();
    assertThat(SanctionAction.fromCode("TERMINATE")).isEmpty();
  }
}
