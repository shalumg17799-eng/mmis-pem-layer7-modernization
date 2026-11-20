package gov.medicaid.pem.tmsis.application;

import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.util.List;

/** The legacy TMSISOUT file (ADR-005): 120-byte records in the order given. */
public interface TmsisFile {

  byte[] write(List<TmsisRecord> records);
}
