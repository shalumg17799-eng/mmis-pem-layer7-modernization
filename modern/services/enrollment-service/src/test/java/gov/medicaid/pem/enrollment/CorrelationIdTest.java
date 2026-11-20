package gov.medicaid.pem.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-36 AC-4 on enrollment-service: an application sent without X-Correlation-Id gets one id,
 * returned in the answer, written on the service's log line and carried by ApplicationValidated.
 */
class CorrelationIdTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  /** Keeps level, message and the MDC correlation id as they were in the logging thread. */
  static final class Captured extends AppenderBase<ILoggingEvent> {
    record Line(Level level, String message, String correlationId) {}

    final List<Line> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
      lines.add(
          new Line(
              e.getLevel(), e.getFormattedMessage(), e.getMDCPropertyMap().get("correlationId")));
    }
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-4] an application without X-Correlation-Id gets a generated id in the answer, the log line and ApplicationValidated")
  void generatedIdFollowsTheApplication() throws Exception {
    String npi = "1003000362";
    jdbc.update(
        "DELETE FROM application_error WHERE application_id IN (SELECT id FROM application WHERE npi = ?)",
        npi);
    jdbc.update("DELETE FROM application WHERE npi = ?", npi);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
    String body =
        json.writeValueAsString(
            Map.of(
                "npi",
                npi,
                "medicaidId",
                "M00000362",
                "providerType",
                "IN",
                "taxonomy",
                "207Q00000X",
                "individual",
                Map.of(
                    "lastName",
                    "RIVERA",
                    "firstName",
                    "MARIA",
                    "licenseNo",
                    "MD12345",
                    "licenseState",
                    "TX")));
    Captured log = new Captured();
    log.start();
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    root.addAppender(log);
    MvcResult r;
    try {
      r =
          mvc.perform(
                  post("/api/v1/applications")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body)
                      .with(PemJwt.provider(npi)))
              .andReturn();
    } finally {
      root.detachAppender(log);
    }
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    String id = r.getResponse().getHeader("X-Correlation-Id");
    assertThat(id).isNotBlank();
    List<String> envelopes =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ApplicationValidated' AND subject = ?",
            String.class,
            npi);
    assertThat(envelopes).hasSize(1);
    assertThat(json.readTree(envelopes.get(0)).path("correlationId").asText()).isEqualTo(id);
    assertThat(log.lines)
        .anyMatch(
            l ->
                l.level() == Level.INFO
                    && l.message().contains(npi)
                    && id.equals(l.correlationId()));
  }
}
