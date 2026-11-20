package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.TrackedProvider;

/** The tracked_provider read model (status and due date from ProviderStatusChanged, D-014). */
public interface ProviderTracking {

  void track(TrackedProvider provider);
}
