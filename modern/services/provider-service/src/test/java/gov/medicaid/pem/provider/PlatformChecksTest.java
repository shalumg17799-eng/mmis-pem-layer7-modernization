package gov.medicaid.pem.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.medicaid.pem.testsupport.PemArchitecture;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Platform checks on the first service: layering, schema, health, protected actuator, correlation
 * id.
 */
class PlatformChecksTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "[MAM-16 AC-5] provider-service respects the layering rules (domain, application, api, infrastructure)")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.provider");
  }

  @Test
  @DisplayName(
      "[MAM-24 AC-3] Flyway V1 creates the provider schema with the outbox and processed-event tables")
  void schema() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
            String.class);
    assertThat(tables)
        .contains("provider", "provider_owner", "audit_log", "outbox_event", "processed_event");
  }

  @Test
  @DisplayName("[MAM-36 AC-1] health answers UP without a token")
  void health() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[MAM-36 AC-2] info and metrics need an ADMIN token")
  void protectedActuator() throws Exception {
    mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/metrics").with(PemJwt.analyst())).andExpect(status().isForbidden());
    mvc.perform(get("/actuator/info").with(PemJwt.admin())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[MAM-36 AC-4] the X-Correlation-Id is echoed on every answer")
  void correlationId() throws Exception {
    mvc.perform(get("/actuator/health/readiness").header("X-Correlation-Id", "walk-1"))
        .andExpect(header().string("X-Correlation-Id", "walk-1"));
  }
}
