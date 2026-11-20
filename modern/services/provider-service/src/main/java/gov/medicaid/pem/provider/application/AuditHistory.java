package gov.medicaid.pem.provider.application;

import java.util.List;

/** Read port of the provider history (MAM-40): oldest first, paged. */
public interface AuditHistory {

  record Page(List<AuditEntry> items, long total) {}

  Page of(String npi, int page, int size);
}
