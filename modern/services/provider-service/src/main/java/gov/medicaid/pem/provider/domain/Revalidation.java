package gov.medicaid.pem.provider.domain;

import java.time.LocalDate;

/**
 * A passed revalidation (MAM-30, D-003, Q-A4): the next due date is the completion date plus five
 * years, same day and month with a 4-digit year (D-004; 29 February gives 28 February, Q-A3). It
 * replaces the legacy daily reset of PRVRSK01 SET-REVALIDATION-DATE; nothing else changes.
 */
public final class Revalidation {

  private Revalidation() {}

  public static Provider passed(Provider p, LocalDate completedOn) {
    return p.withRevalidationDueDate(completedOn.plusYears(Approval.REVALIDATION_YEARS));
  }
}
