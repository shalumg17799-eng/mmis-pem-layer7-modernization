package gov.medicaid.pem.directory.application;

import java.util.List;

/** One page of published providers (contract DirectoryPage). */
public record DirectoryPage(List<DirectoryEntry> items, int page, int size, long total) {

  public DirectoryPage {
    items = List.copyOf(items);
  }
}
