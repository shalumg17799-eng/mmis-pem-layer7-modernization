package gov.medicaid.pem.screening.application;

import java.util.List;

/** One page of screenings (contract ScreeningPage). */
public record ScreeningPage(List<Screening> items, int page, int size, long total) {

  public ScreeningPage {
    items = List.copyOf(items);
  }
}
