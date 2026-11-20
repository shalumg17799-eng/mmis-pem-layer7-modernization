package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.screening.domain.SourceResult;
import java.util.List;

/**
 * Port to the four external sources: one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF.
 */
public interface ScreeningSources {

  List<SourceResult> check(ProviderFacts facts);
}
