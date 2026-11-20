package gov.medicaid.pem.screening.application;

/** Port to provider-service, the one owner of provider data (D-014). */
public interface ProviderFactsSource {

  ProviderFacts facts(String npi);

  /**
   * MAM-20 AC-5: provider-service answered 5xx or did not answer. The provider is never screened on
   * made-up data; the event that asked for the screening is processed again once it answers.
   */
  final class Unavailable extends RuntimeException {

    public Unavailable(String npi, Throwable cause) {
      super("provider-service could not be read for NPI " + npi + ": " + cause, cause);
    }

    /** True when the failure, or any of its causes, is a provider read failure. */
    public static boolean in(Throwable t) {
      for (Throwable c = t; c != null; c = c.getCause()) {
        if (c instanceof Unavailable) {
          return true;
        }
      }
      return false;
    }
  }
}
