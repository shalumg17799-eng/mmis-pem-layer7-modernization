package gov.medicaid.pem.screening.application;

import java.util.Optional;
import java.util.UUID;

/** Persistence port of screening_db: screenings are added, never overwritten (D-018). */
public interface ScreeningStore {

  void save(Screening screening);

  ScreeningPage find(String npi, String result, int page, int size);

  Optional<Screening> get(UUID screeningId);
}
