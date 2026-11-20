package gov.medicaid.pem.notification.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Steps for the failed-send handling of the MAM-59 notices (code review 2026-11-20, MUST-FIX 1;
 * D-024 as in MAM-58 AC-6 and AC-7; MAM-60 AC-5). The fault is a notice template that is not set
 * up: it is renamed in the shared database for the scenario and put back afterwards, so every
 * application context of the test JVM sees it, whichever one consumes the Kafka event. The warning
 * lines are read from the root logger of the JVM.
 */
public class NoticeFailureSteps {

  @Autowired private JdbcTemplate jdbc;

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();
  private String renamed;

  @Given("the {string} notice template is missing")
  public void templateIsMissing(String code) {
    jdbc.update("UPDATE notice_template SET code = ? WHERE code = ?", code + "_X", code);
    renamed = code;
    log.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(log);
  }

  @After
  public void putTheTemplateBack() {
    if (renamed != null) {
      ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(log);
      log.stop();
      jdbc.update("UPDATE notice_template SET code = ? WHERE code = ?", renamed, renamed + "_X");
      renamed = null;
    }
  }

  @Then("the log holds the warning {string} with the cause {string}")
  public void warning(String start, String cause) {
    List<String> warnings =
        List.copyOf(log.list).stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    assertThat(warnings)
        .as("WARN lines")
        .anyMatch(m -> m.startsWith(start) && m.endsWith(", cause: " + cause));
  }
}
