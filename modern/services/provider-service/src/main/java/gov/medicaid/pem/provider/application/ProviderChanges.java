package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;

/** Write port for single-field changes of a provider record; false when the NPI is not on file. */
public interface ProviderChanges {

  boolean setScreenFlag(String npi, ScreenFlag flag);

  boolean setRiskTier(String npi, RiskTier tier);
}
