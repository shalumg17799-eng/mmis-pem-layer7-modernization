package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.tmsis.application.StartTmsisExtract;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The monthly T-MSIS extract (PRVMTH01; MAM-75 AC-7, ADR-004): the cron is in configuration
 * ({@code pem.tmsis.cron}, "-" turns it off) and the business date is the injected Clock's date.
 */
@Component
public class TmsisSchedule {

  private final StartTmsisExtract start;

  public TmsisSchedule(StartTmsisExtract start) {
    this.start = start;
  }

  @Scheduled(cron = "${pem.tmsis.cron:0 0 22 L * *}", zone = "${pem.tmsis.zone:UTC}")
  public void monthly() {
    start.startForToday();
  }
}
