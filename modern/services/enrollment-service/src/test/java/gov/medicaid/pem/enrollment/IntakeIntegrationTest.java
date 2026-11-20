package gov.medicaid.pem.enrollment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.enrollment.application.IntakeRuns;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base of the integration tests of the APPINPUT intake (MAM-56, MAM-57, Lane 3 tests): the real
 * application with Postgres and Kafka from Testcontainers, in the same Spring context as the other
 * tests of the service (no extra context: the test JVM has a small heap). Failures of the real
 * database are made with triggers that reject one kind of write (the way the sanctions and
 * directory tests do it) or by renaming a column, and put right after every test. A run goes on in
 * a background thread of the service: no test leaves one running into the clean-up of the next, and
 * none can wait for ever (every wait is bounded, and the class has a time limit of three minutes
 * per test).
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
public abstract class IntakeIntegrationTest extends IntegrationTest {

  /** The business date of the usual runs of the MAM-56 and MAM-57 acceptance criteria. */
  protected static final String DATE = "2026-03-31";

  @Autowired protected IntakeRuns runs;
  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper json;

  private DbRejections rejections;
  private Captured captured;
  private final List<Runnable> cleanups = new ArrayList<>();

  /** Keeps level, logger, message, the MDC id and the exception chain of every line. */
  public static final class Captured extends AppenderBase<ILoggingEvent> {
    public record Line(
        String level, String logger, String message, String correlationId, String throwable) {

      /** The message and the exception chain together: everything the line says. */
      public String everything() {
        return message + " " + throwable;
      }
    }

    public final List<Line> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
      lines.add(
          new Line(
              e.getLevel().toString(),
              e.getLoggerName(),
              e.getFormattedMessage(),
              e.getMDCPropertyMap().get("correlationId"),
              chain(e.getThrowableProxy())));
    }

    private static String chain(IThrowableProxy proxy) {
      StringBuilder text = new StringBuilder();
      for (IThrowableProxy p = proxy; p != null; p = p.getCause()) {
        text.append(p.getClassName()).append(": ").append(p.getMessage()).append(" | ");
      }
      return text.toString();
    }

    /** The messages of all lines captured so far. */
    public List<String> messages() {
      return lines.stream().map(Line::message).toList();
    }
  }

  @BeforeEach
  protected void cleanAndListen() {
    rejections = new DbRejections(jdbc);
    wipe();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    captured = new Captured();
    captured.setContext(root.getLoggerContext());
    captured.start();
    root.addAppender(captured);
  }

  /** Lifts every rejection once the background jobs of the test have ended, then cleans up. */
  @AfterEach
  protected void endBackgroundJobsAndClean() {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(captured);
    captured.stop();
    backgroundJobsHaveEnded();
    for (Runnable undo : cleanups) {
      undo.run();
    }
    cleanups.clear();
    rejections.liftAll();
    wipe();
  }

  /** Everything the intake writes, in the order the foreign keys allow. */
  protected void wipe() {
    jdbc.update("DELETE FROM application_error");
    jdbc.update("DELETE FROM application");
    jdbc.update("DELETE FROM intake_run");
    jdbc.update("DELETE FROM outbox_event");
  }

  /** The log lines captured since the test began. */
  protected Captured log() {
    return captured;
  }

  /** An intake run goes on in a background thread: wait (bounded) until none is still running. */
  protected void backgroundJobsHaveEnded() {
    Awaitility.await("no intake job is still running")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_job_execution"
                            + " WHERE status IN ('STARTING', 'STARTED')",
                        Integer.class)
                    == 0);
  }

  /**
   * Makes the database reject the writes of {@code events} on {@code table} that match {@code
   * when}.
   */
  protected void reject(String name, String events, String table, String when) {
    rejections.reject(name, events, table, when);
  }

  /** Lets the writes through again. */
  protected void lift(String name) {
    rejections.lift(name);
  }

  /** Registers what puts the database right again after this test, whatever the test did. */
  protected void afterTest(Runnable undo) {
    cleanups.add(undo);
  }

  // ---- reading the results --------------------------------------------------------------------

  /** The count of rows a SELECT count(*) returns. */
  protected int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  /** Applications stored for the NPI. */
  protected int applications(String npi) {
    return count("SELECT count(*) FROM application WHERE npi = ?", npi);
  }

  /** Status of the application stored for the NPI (the first one stored). */
  protected String statusOf(String npi) {
    return jdbc.queryForObject(
        "SELECT status FROM application WHERE npi = ? ORDER BY id LIMIT 1", String.class, npi);
  }

  /** The envelopes of the outbox events of this type, oldest first. */
  protected List<JsonNode> events(String type) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    for (String payload :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = ? ORDER BY created_at, id",
            String.class,
            type)) {
      out.add(json.readTree(payload));
    }
    return out;
  }

  /** The subjects (NPIs) of the outbox events of this type, oldest first. */
  protected List<String> subjects(String type) {
    return jdbc.queryForList(
        "SELECT subject FROM outbox_event WHERE type = ? ORDER BY created_at, id",
        String.class,
        type);
  }
}
