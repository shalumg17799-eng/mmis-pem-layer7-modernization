package gov.medicaid.pem.enrollment.application;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * getApplication (MAM-54): staff read every application; a PROVIDER reads only an application they
 * submitted or that carries their own NPI (D-023), the same rule as the list.
 */
@Service
public class ReadApplication {

  private final ApplicationReader reader;

  public ReadApplication(ApplicationReader reader) {
    this.reader = reader;
  }

  @Transactional(readOnly = true)
  public StoredApplication byId(UUID applicationId, CurrentUser user) {
    OwnedApplication a =
        reader
            .byId(applicationId)
            .orElseThrow(() -> ApiException.notFound("No application " + applicationId));
    if (user.hasRole("PROVIDER") && !user.isStaff()) {
      boolean submitter = user.name().equals(a.submittedBy());
      boolean ownNpi = user.npi().map(n -> n.equals(a.application().npi())).orElse(false);
      if (!submitter && !ownNpi) {
        throw new AccessDeniedException("application " + applicationId + " is not yours");
      }
    }
    return a.application();
  }
}
