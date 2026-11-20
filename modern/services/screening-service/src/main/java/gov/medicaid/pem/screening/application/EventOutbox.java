package gov.medicaid.pem.screening.application;

/** The transactional outbox: the event is written in the same transaction as the screening. */
public interface EventOutbox {

  void enqueue(String type, String subject, Object data);
}
