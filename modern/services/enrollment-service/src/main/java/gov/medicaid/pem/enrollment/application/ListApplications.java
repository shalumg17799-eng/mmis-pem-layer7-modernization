package gov.medicaid.pem.enrollment.application;

import gov.medicaid.pem.kit.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * listApplications: a PROVIDER sees only their own applications (D-023); staff see every
 * application.
 */
@Service
public class ListApplications {

  private final ApplicationStore store;

  public ListApplications(ApplicationStore store) {
    this.store = store;
  }

  @Transactional(readOnly = true)
  public ApplicationPage list(String status, String npi, int page, int size, CurrentUser user) {
    boolean provider = user.hasRole("PROVIDER");
    return store.find(
        new ApplicationQuery(
            status,
            npi,
            provider ? user.name() : null,
            provider ? user.npi().orElse(null) : null,
            page,
            size));
  }
}
