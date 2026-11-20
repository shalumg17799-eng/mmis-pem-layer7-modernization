package gov.medicaid.pem.risk.application;

/** The transactional outbox: the event is written in the same transaction as the assessment. */
public interface EventOutbox {

  void enqueue(String type, String subject, Object data);
}
