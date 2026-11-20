package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.LogCapture;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * What a failed read of the provider records looks like to the caller and to the operator (MAM-37
 * AC-8, D-024). The failure is injected by taking the provider table away for the length of one
 * request (the same database error as an unreachable database, without the 30 s connection wait;
 * the real outage is ProviderOutageTest).
 */
class ProviderReadFailureTest extends IntegrationTest {

  static final String CORRELATION = "7d2f9c4e-1b3a-4c5d-8e6f-000000000123";
  static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual("1003000111").insert(jdbc);
  }

  private MvcResult whileUnreadable(MockHttpServletRequestBuilder request) throws Exception {
    jdbc.execute("ALTER TABLE provider RENAME TO provider_unreadable");
    try {
      return mvc.perform(request).andReturn();
    } finally {
      jdbc.execute("ALTER TABLE provider_unreadable RENAME TO provider");
    }
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-8] the failure is logged with the whole exception (type, message, cause and stack), not only its text (D-024)")
  void fullExceptionIsLogged() throws Exception {
    try (LogCapture log = LogCapture.start()) {
      whileUnreadable(
          get("/api/v1/providers/1003000111")
              .header("X-Correlation-Id", CORRELATION)
              .with(PemJwt.analyst()));

      List<LogCapture.Line> mine =
          log.lines().stream()
              .filter(l -> l.logger().endsWith("ProviderProblems") && l.level() == Level.ERROR)
              .toList();
      assertThat(mine).as("ERROR lines of the problem handler").hasSize(1);
      assertThat(mine.get(0).correlationId()).isEqualTo(CORRELATION);
      assertThat(mine.get(0).exception())
          .as("the exception attached to the log line (stack trace and causes)")
          .isNotNull();
    }
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-8] the problem gives a short reason (at most 200 characters) with no SQL text, table, driver or class name")
  void reasonIsShortAndPlain() throws Exception {
    MvcResult r = whileUnreadable(get("/api/v1/providers/1003000111").with(PemJwt.analyst()));

    assertThat(r.getResponse().getStatus()).isEqualTo(503);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    String detail = problem.path("detail").asText();
    assertThat(detail).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(problem.path("title").asText()).isEqualTo("Service Unavailable");
    assertThat(r.getResponse().getContentAsString().toLowerCase())
        .doesNotContain("select")
        .doesNotContain("insert")
        .doesNotContain("sql")
        .doesNotContain("provider_unreadable")
        .doesNotContain("relation")
        .doesNotContain("postgres")
        .doesNotContain("jdbc")
        .doesNotContain("hibernate")
        .doesNotContain("org.springframework")
        .doesNotContain("exception");
  }

  @Test
  @DisplayName("[MAM-37 AC-8] a failing search is a 503 problem as well, never an empty page")
  void failingSearchIsNotAnEmptyPage() throws Exception {
    MvcResult r =
        whileUnreadable(get("/api/v1/providers").param("npi", "1003000111").with(PemJwt.analyst()));

    assertThat(r.getResponse().getStatus()).isEqualTo(503);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(r.getResponse().getContentAsString()).doesNotContain("\"items\"");
  }

  @Test
  @DisplayName("[MAM-43 AC-1] a failing export is a 503 problem, never a short or empty file")
  void failingExportIsNotAnEmptyFile() throws Exception {
    MvcResult r = whileUnreadable(get("/api/v1/legacy/provider-master").with(PemJwt.admin()));

    assertThat(r.getResponse().getStatus()).isEqualTo(503);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-7] a failure while checking the provider for the history is a 503 problem, not a 404 and not an empty history")
  void failingHistoryIsNotNotFound() throws Exception {
    MvcResult r =
        whileUnreadable(get("/api/v1/providers/1003000111/audit").with(PemJwt.supervisor()));

    assertThat(r.getResponse().getStatus()).isEqualTo(503);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(r.getResponse().getContentAsString()).doesNotContain("\"items\"");
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-8] the answer to a failure still carries the X-Correlation-Id of the request")
  void correlationIdIsEchoed() throws Exception {
    MvcResult r =
        whileUnreadable(
            get("/api/v1/providers/1003000111")
                .header("X-Correlation-Id", CORRELATION)
                .with(PemJwt.analyst()));

    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo(CORRELATION);
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-8] an X-Correlation-Id with a line break or a forged log line is replaced by a generated id, in the answer and in every log line")
  void unsafeCorrelationIdNeverReachesTheLog() throws Exception {
    String forged = "abc\nERROR forged line from the caller";
    try (LogCapture log = LogCapture.start()) {
      MvcResult r =
          whileUnreadable(
              get("/api/v1/providers/1003000111")
                  .header("X-Correlation-Id", forged)
                  .with(PemJwt.analyst()));

      String echoed = r.getResponse().getHeader("X-Correlation-Id");
      assertThat(echoed).isNotEqualTo(forged).matches(SAFE);
      assertThat(echoed).hasSize(36);
      assertThat(log.lines())
          .allSatisfy(
              l -> {
                assertThat(l.message()).doesNotContain("forged");
                if (l.correlationId() != null) {
                  assertThat(l.correlationId()).matches(SAFE);
                }
              });
      assertThat(log.lines())
          .anyMatch(
              l -> l.logger().endsWith("ProviderProblems") && echoed.equals(l.correlationId()));
    }
  }
}
