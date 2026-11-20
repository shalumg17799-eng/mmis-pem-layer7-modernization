package gov.medicaid.pem.notification.infrastructure;

import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.notification.application.Notice;
import gov.medicaid.pem.notification.application.NoticePorts;
import gov.medicaid.pem.notification.application.Template;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** notification_db (templates, notices), SMTP and the outbox bridge. */
@Configuration
public class NoticeAdapters {

  static final String FROM = "notices@pem.local";

  @Bean
  NoticePorts.Templates templates(JdbcTemplate jdbc) {
    return code ->
        jdbc
            .query(
                "SELECT code, subject, body FROM notice_template WHERE code = ?",
                (rs, n) -> new Template(rs.getString(1), rs.getString(2), rs.getString(3)),
                code)
            .stream()
            .findFirst();
  }

  @Bean
  NoticePorts.NoticeStore noticeStore(JdbcTemplate jdbc) {
    return (Notice n) ->
        jdbc.update(
            "INSERT INTO notice (public_id, npi, template_code, channel, recipient, status, event_id, sent_at)"
                + " VALUES (?, ?, ?, 'EMAIL', ?, 'SENT', ?, ?)",
            n.noticeId(),
            n.npi(),
            n.templateCode(),
            n.recipient(),
            n.eventId(),
            Timestamp.from(n.sentAt()));
  }

  /** MAM-58: a notice that could not be sent (no sent_at); the cause is in the warning log. */
  @Bean
  NoticePorts.FailedNotices failedNotices(JdbcTemplate jdbc) {
    return (Notice n, String cause) ->
        jdbc.update(
            "INSERT INTO notice (public_id, npi, template_code, channel, recipient, status, event_id)"
                + " VALUES (?, ?, ?, 'EMAIL', ?, 'FAILED', ?)",
            n.noticeId(),
            n.npi(),
            n.templateCode(),
            n.recipient(),
            n.eventId());
  }

  @Bean
  NoticePorts.Mailer mailer(JavaMailSender mail) {
    return (to, subject, body) -> {
      SimpleMailMessage m = new SimpleMailMessage();
      m.setFrom(FROM);
      m.setTo(to);
      m.setSubject(subject);
      m.setText(body);
      mail.send(m);
    };
  }

  @Bean
  NoticePorts.EventOutbox eventOutbox(OutboxWriter writer) {
    return writer::enqueue;
  }

  static Optional<String> text(com.fasterxml.jackson.databind.JsonNode n, String field) {
    var v = n.path(field);
    return v.isMissingNode() || v.isNull() ? Optional.empty() : Optional.of(v.asText());
  }
}
