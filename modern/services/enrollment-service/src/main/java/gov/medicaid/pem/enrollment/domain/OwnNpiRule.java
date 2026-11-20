package gov.medicaid.pem.enrollment.domain;

import java.util.Optional;

/**
 * Q-095 (user decision 2026-11-19): a provider may submit an individual application (legacy type
 * IN, including feed codes mapped to IN) only under the NPI in their own token. Group, facility and
 * unknown types are not restricted. A missing or malformed NPI is no other provider's NPI: it is
 * left to the E101 check (MAM-46 AC-2).
 */
public final class OwnNpiRule {

  static final String INDIVIDUAL = "IN";

  private OwnNpiRule() {}

  public static boolean allows(String legacyType, String npi, Optional<String> tokenNpi) {
    if (!INDIVIDUAL.equals(legacyType) || !ApplicationValidator.isNpi(npi)) {
      return true;
    }
    return tokenNpi.map(npi::equals).orElse(false);
  }
}
