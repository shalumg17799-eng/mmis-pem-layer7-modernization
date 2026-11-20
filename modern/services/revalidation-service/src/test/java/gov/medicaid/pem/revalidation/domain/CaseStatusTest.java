package gov.medicaid.pem.revalidation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The four states of a revalidation case (D-029): DUE and IN_SCREENING are open; COMPLETED and
 * CANCELLED are closed. The contract's status enum has exactly these four names.
 */
class CaseStatusTest {

  @ParameterizedTest(name = "[MAM-48 AC-3] the status {0} is open: {1}")
  @CsvSource({
    "DUE, true",
    "IN_SCREENING, true",
    "COMPLETED, false",
    "CANCELLED, false",
  })
  void openStatuses(CaseStatus status, boolean open) {
    assertThat(status.open()).isEqualTo(open);
  }

  @Test
  @DisplayName("[MAM-49 AC-1] the contract's four statuses and no other")
  void theFourStatusesOfTheContract() {
    assertThat(CaseStatus.values())
        .extracting(Enum::name)
        .containsExactly("DUE", "IN_SCREENING", "COMPLETED", "CANCELLED");
  }

  @Test
  @DisplayName("[MAM-48 AC-1] a status name that is not one of the four is refused by valueOf")
  void unknownNameIsRefused() {
    for (String name : new String[] {"OPEN", "due", "Due", "", "CLOSED"}) {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> CaseStatus.valueOf(name))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
