package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.util.List;

/** The legacy REVALQ file of the cases a run opened (ADR-005). */
public interface QueueFile {

  byte[] write(List<RevalidationCase> opened);
}
