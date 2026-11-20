package gov.medicaid.pem.revalidation.domain;

import java.time.LocalDate;

/**
 * PRVREV01 CHECK-NEXT (81-84): an ACTIVE provider whose due date is on or before the business date
 * is queued. Only ACTIVE providers are swept (D-006); the date compared is the explicit business
 * date (D-020) with 4-digit years (D-004); an ACTIVE provider without a due date is not queued but
 * counted (Q-049, C-11 answer A).
 */
public final class SweepRule {

  static final String ACTIVE = "A";

  private SweepRule() {}

  public static SweepDecision decide(TrackedProvider p, LocalDate businessDate) {
    if (!ACTIVE.equals(p.enrollStatus())) {
      return SweepDecision.NOT_ACTIVE;
    }
    if (p.dueDate() == null) {
      return SweepDecision.NO_DUE_DATE;
    }
    return p.dueDate().isAfter(businessDate) ? SweepDecision.NOT_DUE : SweepDecision.QUEUE;
  }
}
