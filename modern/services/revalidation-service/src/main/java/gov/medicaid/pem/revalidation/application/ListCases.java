package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** listRevalidationCases: the cases, filtered by NPI and status, one page at a time. */
@Service
public class ListCases {

  private final CaseStore cases;

  public ListCases(CaseStore cases) {
    this.cases = cases;
  }

  @Transactional(readOnly = true)
  public CasePage page(String npi, CaseStatus status, int page, int size) {
    return cases.find(npi, status, page, size);
  }
}
