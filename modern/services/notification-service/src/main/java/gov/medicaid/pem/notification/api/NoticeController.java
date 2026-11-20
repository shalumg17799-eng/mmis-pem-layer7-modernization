package gov.medicaid.pem.notification.api;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.notification.application.ListNotices;
import gov.medicaid.pem.notification.application.NoticePage;
import gov.medicaid.pem.notification.application.Template;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** listNotices and listNoticeTemplates (contract notification-service.yaml, MAM-60). */
@RestController
public class NoticeController {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");

  private final ListNotices list;

  public NoticeController(ListNotices list) {
    this.list = list;
  }

  /** x-roles PROVIDER (own NPI only), ANALYST, SUPERVISOR, ADMIN. */
  @GetMapping("/api/v1/notices")
  @PreAuthorize("hasAnyRole('PROVIDER', 'ANALYST', 'SUPERVISOR', 'ADMIN')")
  public Map<String, Object> listNotices(
      @RequestParam(required = false) String npi,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (npi != null && !NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("npi must be 10 digits.");
    }
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
    NoticePage p = list.notices(npi, page, size, CurrentUser.current());
    return Map.of("items", p.items(), "page", p.page(), "size", p.size(), "total", p.total());
  }

  /** x-roles ADMIN. */
  @GetMapping("/api/v1/notice-templates")
  @PreAuthorize("hasRole('ADMIN')")
  public List<Template> listNoticeTemplates() {
    return list.templates();
  }
}
