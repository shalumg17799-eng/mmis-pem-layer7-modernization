package gov.medicaid.pem.provider.domain;

/**
 * A decision that the provider's state does not allow: a conflict when the enrolment was already
 * decided (409), else a business rule that is not met, such as approving without a passed screening
 * (422).
 */
public class DecisionRefused extends RuntimeException {

  private final boolean conflict;

  public DecisionRefused(boolean conflict, String message) {
    super(message);
    this.conflict = conflict;
  }

  public boolean conflict() {
    return conflict;
  }
}
