package gov.medicaid.pem.provider.application;

/** Operational counters of the sanction status changes (PRVSAN01 WS-TERM-COUNT, 108). */
public interface StatusChangeMetrics {

  /** A provider was terminated. */
  void terminated();
}
