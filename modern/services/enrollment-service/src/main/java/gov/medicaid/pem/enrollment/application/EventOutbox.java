package gov.medicaid.pem.enrollment.application;

/** The transactional outbox: the event is written in the same transaction as the application. */
public interface EventOutbox {

  void enqueue(String type, String subject, Object data);
}
