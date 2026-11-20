package gov.medicaid.pem.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/**
 * Collects every log event of the test JVM while it is open (the root logger, so the Kafka listener
 * threads are included). The MDC of each event is read on the logging thread, so the correlation id
 * that was current when the line was written can be checked afterwards.
 */
public final class LogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {

  private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

  private LogCapture() {}

  public static LogCapture open() {
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    LogCapture capture = new LogCapture();
    capture.setContext(root.getLoggerContext());
    capture.start();
    root.addAppender(capture);
    return capture;
  }

  @Override
  protected void append(ILoggingEvent event) {
    event.getFormattedMessage();
    event.getMDCPropertyMap();
    events.add(event);
  }

  public List<ILoggingEvent> events() {
    return List.copyOf(events);
  }

  /** The formatted messages of the events at this level, oldest first. */
  public List<String> messages(Level level) {
    return events().stream()
        .filter(e -> e.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  /** The formatted messages of every event at this level or above that contain the text. */
  public List<String> messagesAtLeast(Level level, String text) {
    return events().stream()
        .filter(e -> e.getLevel().isGreaterOrEqual(level))
        .map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains(text))
        .toList();
  }

  /** The MDC of an event as it was when the line was written (never null). */
  public static Map<String, String> mdc(ILoggingEvent event) {
    Map<String, String> m = event.getMDCPropertyMap();
    return m == null ? Map.of() : m;
  }

  @Override
  public void close() {
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    root.detachAppender(this);
    stop();
  }
}
