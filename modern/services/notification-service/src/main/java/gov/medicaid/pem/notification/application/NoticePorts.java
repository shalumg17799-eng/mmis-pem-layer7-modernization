package gov.medicaid.pem.notification.application;

import java.util.List;
import java.util.Optional;

/** The ports of the notice use case. */
public final class NoticePorts {

  private NoticePorts() {}

  /** notice_template by code. */
  public interface Templates {
    Optional<Template> find(String code);
  }

  /** The email channel (SMTP; Mailpit locally). */
  public interface Mailer {
    void send(String to, String subject, String body);
  }

  /** The notice table. */
  public interface NoticeStore {
    void save(Notice notice);
  }

  /** The notice table: a notice that could not be sent is kept with status FAILED (D-024). */
  public interface FailedNotices {
    void save(Notice notice, String cause);
  }

  /** The notice log and templates, read side (MAM-60). */
  public interface NoticeLog {
    /** Newest first; npi null means every provider. */
    NoticePage find(String npi, int page, int size);

    List<Template> templates();
  }

  /** The transactional outbox. */
  public interface EventOutbox {
    void enqueue(String type, String subject, Object data);
  }
}
