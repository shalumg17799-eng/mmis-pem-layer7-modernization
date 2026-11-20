package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Follows ProviderStatusChanged (read model, D-014): the tracked status and due date always follow,
 * and a provider that is no longer ACTIVE cancels its open case (MAM-49, C-14, D-006).
 */
@Service
public class FollowProvider {

  private static final Logger LOG = LoggerFactory.getLogger(FollowProvider.class);
  static final String ACTIVE = "A";

  private final ProviderTracking tracking;
  private final CaseCompletions cases;
  private final Clock clock;

  public FollowProvider(ProviderTracking tracking, CaseCompletions cases, Clock clock) {
    this.tracking = tracking;
    this.cases = cases;
    this.clock = clock;
  }

  @Transactional
  public void statusChanged(TrackedProvider p) {
    tracking.track(p);
    if (!ACTIVE.equals(p.enrollStatus())) {
      cases
          .openCaseOf(p.npi())
          .ifPresent(
              c -> {
                cases.close(c.caseId(), CaseStatus.CANCELLED, null, clock.instant());
                LOG.info(
                    "Revalidation case {} of NPI {} CANCELLED: status {}",
                    c.caseId(),
                    p.npi(),
                    p.enrollStatus());
              });
    }
  }
}
