package gov.medicaid.pem.enrollment.application;

import java.util.List;

/** One page of applications (contract ApplicationPage). */
public record ApplicationPage(List<StoredApplication> items, int page, int size, long total) {

  public ApplicationPage {
    items = List.copyOf(items);
  }
}
