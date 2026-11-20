package gov.medicaid.pem.tmsis.domain;

import java.time.LocalDate;

/**
 * PRVTMS01 CHECK-FUTURE-ENROLL (R-PRVTMS01-cbl-00005, -00035): an ACTIVE provider whose enrolment
 * year is strictly later than the year of the business date is flagged. The legacy compared
 * two-digit years; with D-004 the full four-digit years are compared (Q-062 default B), so 1998 is
 * not later than 2026 and 2101 is. The flag only warns: the record is still reported unchanged
 * (R-PRVTMS01-cbl-00006).
 */
public final class FutureEnrolment {

  private FutureEnrolment() {}

  public static boolean flagged(LocalDate effectiveDate, LocalDate businessDate) {
    return effectiveDate != null && effectiveDate.getYear() > businessDate.getYear();
  }
}
