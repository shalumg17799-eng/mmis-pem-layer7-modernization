package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-30: the due date is the approval or revalidation date plus five years, same day and month,
 * with a four-digit year (D-003, D-004); 29 February gives 28 February (Q-A3).
 */
class RevalidationDueDateTest {

  static Provider provider(EnrollStatus status, ScreenFlag flag, LocalDate due) {
    return new Provider(
        "1003000111",
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        RiskTier.LIMITED,
        status,
        flag,
        LocalDate.of(2021, 3, 31),
        null,
        due,
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        4);
  }

  @ParameterizedTest(name = "approved on {0}: due {1}")
  @CsvSource({
    "2029-03-15,2034-03-15",
    "2065-06-30,2070-06-30",
    "2097-04-01,2102-04-01",
    "2028-02-29,2033-02-28"
  })
  @DisplayName(
      "[MAM-30 AC-1/AC-2/AC-3] an approval sets approval date + 5 years: same day and month, no century guess, 29 Feb gives 28 Feb")
  void approval(LocalDate approvedOn, LocalDate due) {
    Provider approved =
        Approval.decide(
            provider(EnrollStatus.PENDING, ScreenFlag.PASS, null), Decision.APPROVE, approvedOn);
    assertThat(approved.revalidationDueDate()).isEqualTo(due);
  }

  @Test
  @DisplayName(
      "[MAM-30 AC-4] a passed revalidation overwrites the due date with completion date + 5 years and keeps the status")
  void passedRevalidation() {
    Provider p = provider(EnrollStatus.ACTIVE, ScreenFlag.PASS, LocalDate.of(2026, 3, 31));
    Provider after = Revalidation.passed(p, LocalDate.of(2026, 4, 2));
    assertThat(after.revalidationDueDate()).isEqualTo(LocalDate.of(2031, 4, 2));
    assertThat(after.enrollStatus()).isEqualTo(EnrollStatus.ACTIVE);
    assertThat(after).usingRecursiveComparison().ignoringFields("revalidationDueDate").isEqualTo(p);
  }

  @ParameterizedTest(name = "revalidated on {0}: due {1}")
  @CsvSource({"2028-02-29,2033-02-28", "2066-01-15,2071-01-15", "2099-12-31,2104-12-31"})
  @DisplayName(
      "[MAM-30 AC-3/AC-4] a revalidation on 29 February or late in the century still gives a real 4-digit-year date")
  void revalidationDates(LocalDate completedOn, LocalDate due) {
    Provider p = provider(EnrollStatus.ACTIVE, ScreenFlag.PASS, LocalDate.of(2026, 3, 31));
    assertThat(Revalidation.passed(p, completedOn).revalidationDueDate()).isEqualTo(due);
  }
}
