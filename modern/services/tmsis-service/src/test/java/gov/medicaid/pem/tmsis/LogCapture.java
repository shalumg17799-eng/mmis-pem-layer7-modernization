package gov.medicaid.pem.tmsis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/**
 * Collects every log event of the test JVM while it is open (an appender on the root logger), from
 * any thread: the request thread of a call and the thread of a background run alike. Use it in a
 * try-with-resources block.
 */
public final class LogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {

  /** One log event as the logging thread produced it. */
  public record Line(
      Level level,
      String logger,
      String message,
      String correlationId,
      boolean hasThrowable,
      String throwableText) {

    /** The message and, if there is one, the whole chain of exception messages. */
    public String everything() {
      return message + " " + throwableText;
    }
  }

  private final List<Line> lines = new CopyOnWriteArrayList<>();
  private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

  private LogCapture() {}

  public static LogCapture open() {
    LogCapture capture = new LogCapture();
    capture.start();
    capture.root.addAppender(capture);
    return capture;
  }

  @Override
  protected void append(ILoggingEvent e) {
    StringBuilder chain = new StringBuilder();
    for (IThrowableProxy t = e.getThrowableProxy(); t != null; t = t.getCause()) {
      chain.append(t.getClassName()).append(": ").append(t.getMessage()).append(" | ");
    }
    lines.add(
        new Line(
            e.getLevel(),
            e.getLoggerName(),
            e.getFormattedMessage(),
            e.getMDCPropertyMap().get("correlationId"),
            e.getThrowableProxy() != null,
            chain.toString()));
  }

  public List<Line> lines() {
    return new ArrayList<>(lines);
  }

  /** The formatted messages of the events at exactly this level. */
  public List<String> messages(Level level) {
    return lines.stream().filter(l -> l.level() == level).map(Line::message).toList();
  }

  @Override
  public void close() {
    root.detachAppender(this);
    stop();
  }
}
