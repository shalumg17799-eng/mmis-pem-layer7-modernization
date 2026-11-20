package gov.medicaid.pem.provider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/**
 * Captures what the application logs while a test runs: level, logger, message, the correlation id
 * of the logging thread and the exception, read at the moment the line is written (the MDC belongs
 * to the thread that logs).
 */
public final class LogCapture implements AutoCloseable {

  /** One captured log line. */
  public record Line(
      Level level,
      String logger,
      String message,
      String correlationId,
      IThrowableProxy exception) {}

  private final List<Line> lines = new CopyOnWriteArrayList<>();
  private final AppenderBase<ILoggingEvent> appender =
      new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent e) {
          lines.add(
              new Line(
                  e.getLevel(),
                  e.getLoggerName(),
                  e.getFormattedMessage(),
                  e.getMDCPropertyMap().get("correlationId"),
                  e.getThrowableProxy()));
        }
      };

  private LogCapture() {}

  public static LogCapture start() {
    LogCapture c = new LogCapture();
    c.appender.start();
    root().addAppender(c.appender);
    return c;
  }

  public List<Line> lines() {
    return List.copyOf(lines);
  }

  @Override
  public void close() {
    root().detachAppender(appender);
    appender.stop();
  }

  private static Logger root() {
    return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }
}
