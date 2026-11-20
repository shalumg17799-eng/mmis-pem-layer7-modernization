package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.kit.web.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** listScreenings and getScreening (staff only; roles are checked on the endpoint, D-023). */
@Service
public class ReadScreenings {

  private final ScreeningStore store;

  public ReadScreenings(ScreeningStore store) {
    this.store = store;
  }

  @Transactional(readOnly = true)
  public ScreeningPage list(String npi, String result, int page, int size) {
    return store.find(npi, result, page, size);
  }

  @Transactional(readOnly = true)
  public Screening get(UUID screeningId) {
    return store
        .get(screeningId)
        .orElseThrow(() -> ApiException.notFound("No screening " + screeningId));
  }
}
