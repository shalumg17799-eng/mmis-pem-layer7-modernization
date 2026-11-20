package gov.medicaid.pem.tmsis.domain;

import java.util.Optional;

/**
 * One TMSISOUT detail (PRVTMS01 WS-TMSIS-DETAIL, 120 bytes): Medicaid ID and NPI copied unchanged,
 * status 'A' and the run's period. Only ACTIVE providers are reported (R-PRVTMS01-cbl-00004,
 * R-PRVTMS01-ADD-04, Q-007); every other status gives no record.
 */
public record TmsisRecord(String medicaidId, String npi, String status, String periodYyddd) {

  public static final String ACTIVE = "A";

  public static Optional<TmsisRecord> of(
      String medicaidId, String npi, String enrollStatus, String periodYyddd) {
    return ACTIVE.equals(enrollStatus)
        ? Optional.of(new TmsisRecord(medicaidId, npi, ACTIVE, periodYyddd))
        : Optional.empty();
  }
}
