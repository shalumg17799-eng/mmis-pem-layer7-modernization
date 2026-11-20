package gov.medicaid.pem.enrollment.application;

/**
 * The failure of an intake run, with a message the intake wrote itself: short, naming the record,
 * and never holding SQL text or bytes of the file. Only such a message is shown to a caller and
 * stored as the reason of the run (D-024); the exception it wraps is logged, never shown.
 */
public class IntakeFailure extends IllegalStateException {

  public IntakeFailure(String message) {
    super(message);
  }

  public IntakeFailure(String message, Throwable cause) {
    super(message, cause);
  }
}
