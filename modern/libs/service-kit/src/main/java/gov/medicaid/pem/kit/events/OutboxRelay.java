package gov.medicaid.pem.kit.events;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes pending outbox rows to the producer's topic, keyed by NPI, and marks them published
 * (at-least-once; the consumers are idempotent). Rows are locked with SKIP LOCKED so two instances
 * never publish the same row twice.
 */
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  static final String SELECT_SQL =
      "SELECT id, subject, payload::text AS payload FROM outbox_event WHERE published_at IS NULL "
          + "ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED";
  static final String MARK_SQL = "UPDATE outbox_event SET published_at = now() WHERE id = ?";
  private static final int BATCH = 50;

  record Row(UUID id, String subject, String payload) {}

  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final TransactionTemplate tx;
  private final String topic;

  public OutboxRelay(
      JdbcTemplate jdbc,
      KafkaTemplate<String, String> kafka,
      TransactionTemplate tx,
      String service) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.tx = tx;
    this.topic = Topics.forService(service);
  }

  @Scheduled(
      fixedDelayString = "${pem.outbox.poll-ms:500}",
      initialDelayString = "${pem.outbox.initial-delay-ms:2000}")
  public void poll() {
    publishPending();
  }

  /** Publishes one batch; returns the number of rows published. */
  public int publishPending() {
    Integer sent =
        tx.execute(
            status -> {
              List<Row> rows =
                  jdbc.query(
                      SELECT_SQL,
                      (rs, i) ->
                          new Row(
                              rs.getObject("id", UUID.class),
                              rs.getString("subject"),
                              rs.getString("payload")),
                      BATCH);
              int ok = 0;
              for (Row row : rows) {
                try {
                  kafka.send(topic, row.subject(), row.payload()).get(10, TimeUnit.SECONDS);
                  jdbc.update(MARK_SQL, row.id());
                  ok++;
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                } catch (ExecutionException | TimeoutException | RuntimeException e) {
                  log.warn(
                      "outbox event {} not published yet (will retry): {}",
                      row.id(),
                      e.getMessage());
                  break;
                }
              }
              return ok;
            });
    return sent == null ? 0 : sent;
  }
}
