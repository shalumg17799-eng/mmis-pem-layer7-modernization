package gov.medicaid.pem.screening.application;

import java.time.LocalDate;
import java.util.List;

/** The legacy SCRNOUT file (SCRNREC, 67 bytes, ADR-005). */
public interface ScrnoutFile {

  byte[] write(List<ScreeningRuns.Screened> screened, LocalDate verdictDate);
}
