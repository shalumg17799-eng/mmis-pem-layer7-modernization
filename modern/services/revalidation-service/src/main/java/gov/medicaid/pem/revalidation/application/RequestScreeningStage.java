package gov.medicaid.pem.revalidation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MAM-49 AC-1 (C-13): once the RevalidationDue of a case is published, the re-screening is
 * requested (screening-service starts it from that event), so the case moves from DUE to
 * IN_SCREENING.
 */
@Service
public class RequestScreeningStage {

  private final CaseStages stages;

  public RequestScreeningStage(CaseStages stages) {
    this.stages = stages;
  }

  @Transactional
  public boolean revalidationDuePublished(UUID caseId) {
    return stages.markInScreening(caseId);
  }
}
