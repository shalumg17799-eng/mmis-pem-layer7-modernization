package gov.medicaid.pem.tmsis.domain;

import java.time.LocalDate;

/**
 * The T-MSIS reporting period of a run (PRVTMS01 WS-T-PERIOD-YYDDD, D-015): the business date of
 * the run (D-020), never the system date, as a two-digit year and a three-digit day of the year.
 * YYDDD is kept only because the legacy file has it (ADR-009).
 */
public final class ReportingPeriod {

  private ReportingPeriod() {}

  public static String yyddd(LocalDate businessDate) {
    return String.format(
        "%02d%03d", Math.floorMod(businessDate.getYear(), 100), businessDate.getDayOfYear());
  }
}
