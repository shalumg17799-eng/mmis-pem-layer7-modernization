package gov.medicaid.pem.sanctions.application;

/** The transactional outbox: the event is written in the same transaction as the sanction row. */
public interface EventOutbox {

  void enqueue(String type, String subject, Object data);
}
