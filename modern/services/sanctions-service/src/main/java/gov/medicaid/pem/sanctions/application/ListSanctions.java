package gov.medicaid.pem.sanctions.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** listSanctions (MAM-52 AC-8): the sanction history, newest first, one page at a time. */
@Service
public class ListSanctions {

  private final SanctionHistory history;

  public ListSanctions(SanctionHistory history) {
    this.history = history;
  }

  @Transactional(readOnly = true)
  public SanctionPage page(String npi, int page, int size) {
    return history.find(npi, page, size);
  }
}
