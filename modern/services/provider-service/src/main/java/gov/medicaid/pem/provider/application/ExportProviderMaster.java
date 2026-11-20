package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * exportProviderMaster (MAM-43, D-007, ADR-005): every provider as a 130-byte PRVRECRD record, in
 * ascending NPI order (the key order of the legacy KSDS), for the Phase 9 comparison.
 */
@Service
public class ExportProviderMaster {

  /** Read port: every provider record in ascending NPI order. */
  public interface AllProviders {
    List<Provider> allByNpi();
  }

  /** Write port: the legacy PRVRECRD records, one per line, no header or trailer. */
  public interface MasterFile {
    String write(List<Provider> providers);
  }

  private final AllProviders providers;
  private final MasterFile file;

  public ExportProviderMaster(AllProviders providers, MasterFile file) {
    this.providers = providers;
    this.file = file;
  }

  @Transactional(readOnly = true)
  public String export() {
    return file.write(providers.allByNpi());
  }
}
