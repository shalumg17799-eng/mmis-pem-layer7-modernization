package gov.medicaid.pem.notification.application;

import gov.medicaid.pem.kit.security.CurrentUser;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * listNotices and listNoticeTemplates (MAM-60). Staff see every provider's notices; a PROVIDER sees
 * only the notices of the NPI in their token, and naming another NPI is refused (D-012, D-023).
 */
@Service
public class ListNotices {

  private final NoticePorts.NoticeLog log;

  public ListNotices(NoticePorts.NoticeLog log) {
    this.log = log;
  }

  @Transactional(readOnly = true)
  public NoticePage notices(String npi, int page, int size, CurrentUser user) {
    if (user.isStaff()) {
      return log.find(npi, page, size);
    }
    String own =
        user.npi().orElseThrow(() -> new AccessDeniedException("no provider NPI in the token"));
    if (npi != null && !npi.equals(own)) {
      throw new AccessDeniedException("provider " + npi + " is not yours");
    }
    return log.find(own, page, size);
  }

  @Transactional(readOnly = true)
  public List<Template> templates() {
    return log.templates();
  }
}
