package gov.medicaid.pem.tmsis.application;

/** Port to the transactional outbox (ADR-003): the event is stored with the change it announces. */
public interface EventOutbox {

  void enqueue(String type, String subject, Object data);
}
