package gov.medicaid.pem.screening;

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

/** Platform checks of screening-service, written with its first stories (MAM-18, MAM-25). */
class PlatformChecksTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[MAM-18] screening-service respects the layering rules")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.screening");
  }

  @Test
  @DisplayName("[MAM-18] Flyway creates the screening, result, outbox and processed-event tables")
  void schema() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
    assertThat(tables).contains("screening", "screening_result", "outbox_event", "processed_event");
  }

  @Test
  @DisplayName("[MAM-18] health answers UP without a token")
  void health() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
