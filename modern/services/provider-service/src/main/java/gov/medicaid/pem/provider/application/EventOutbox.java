package gov.medicaid.pem.provider.application;

/**
 * The transactional outbox: the event is written in the same transaction as the provider record.
 */
public interface EventOutbox {

  void enqueue(String type, String subject, Object data);
}
