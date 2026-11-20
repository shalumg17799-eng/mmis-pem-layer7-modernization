package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes a revalidation case (MAM-48, D-029): on request with a screening result, or by itself
 * when a REVALIDATION ScreeningCompleted arrives for the provider's open case. A PASS or a FAIL
 * both close the case; the next due date is never set here (provider-service owns it, D-003, C-10).
 */
@Service
public class CompleteCase {

  private static final Logger LOG = LoggerFactory.getLogger(CompleteCase.class);
  static final String REVALIDATION = "REVALIDATION";

  private final CaseCompletions cases;
  private final Clock clock;

  public CompleteCase(CaseCompletions cases, Clock clock) {
    this.cases = cases;
    this.clock = clock;
  }

  /** completeRevalidation: 404 for an unknown case, 409 for a case that is already closed. */
  @Transactional
  public RevalidationCase byRequest(UUID caseId, UUID screeningId, String result) {
    RevalidationCase c =
        cases
            .findCase(caseId)
            .orElseThrow(() -> ApiException.notFound("No revalidation case " + caseId));
    if (!c.status().open()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          null,
          "Revalidation case " + caseId + " is already " + c.status() + ".",
          List.of());
    }
    return close(c, screeningId, result);
  }

  /** A ScreeningCompleted: only REVALIDATION results close the provider's open case. */
  @Transactional
  public boolean byScreening(String npi, String reason, String result, UUID screeningId) {
    if (!REVALIDATION.equals(reason)) {
      return false;
    }
    Optional<RevalidationCase> open = cases.openCaseOf(npi);
    if (open.isEmpty()) {
      LOG.info(
          "REVALIDATION screening {} of NPI {}: no open case, nothing to close", screeningId, npi);
      return false;
    }
    close(open.get(), screeningId, result);
    return true;
  }

  private RevalidationCase close(RevalidationCase c, UUID screeningId, String result) {
    Instant at = clock.instant();
    cases.close(c.caseId(), CaseStatus.COMPLETED, screeningId, at);
    LOG.info("Revalidation case {} of NPI {} COMPLETED ({})", c.caseId(), c.npi(), result);
    return new RevalidationCase(
        c.caseId(), c.npi(), c.dueDate(), CaseStatus.COMPLETED, c.openedAt(), at);
  }
}
