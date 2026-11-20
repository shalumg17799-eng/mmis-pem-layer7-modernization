package gov.medicaid.pem.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.medicaid.pem.testsupport.PemArchitecture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Platform checks of enrollment-service, written with its first story (MAM-46). */
class PlatformChecksTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "[MAM-46] enrollment-service respects the layering rules (domain, application, api, infrastructure)")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.enrollment");
  }

  @Test
  @DisplayName(
      "[MAM-46] Flyway creates the application, error, mapping, outbox and processed-event tables")
  void schema() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
            String.class);
    assertThat(tables)
        .contains(
            "application",
            "application_error",
            "provider_type_mapping",
            "outbox_event",
            "processed_event");
  }

  @Test
  @DisplayName("[MAM-46] health answers UP without a token")
  void health() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
