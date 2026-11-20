package gov.medicaid.pem.revalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.medicaid.pem.testsupport.PemArchitecture;
import java.sql.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Platform checks of revalidation-service, written with its first story (MAM-45). */
class PlatformChecksTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clean() {
    jdbc.update("DELETE FROM revalidation_case WHERE npi = '1003000999'");
  }

  @Test
  @DisplayName(
      "[MAM-45] revalidation-service respects the layering rules (domain, application, api, infrastructure)")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.revalidation");
  }

  @Test
  @DisplayName(
      "[MAM-45] Flyway creates the tracked-provider, sweep-run, case, outbox and processed-event tables")
  void schema() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
            String.class);
    assertThat(tables)
        .contains(
            "tracked_provider",
            "sweep_run",
            "revalidation_case",
            "outbox_event",
            "processed_event");
  }

  @Test
  @DisplayName("[MAM-45 AC-5] the database allows at most one open case per provider (D-029)")
  void atMostOneOpenCase() {
    insertCase("DUE");
    assertThatThrownBy(() -> insertCase("IN_SCREENING"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("[MAM-49 AC-4] a closed case does not block a new open case of the provider")
  void closedCasesDoNotBlock() {
    insertCase("CANCELLED");
    insertCase("COMPLETED");
    insertCase("DUE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM revalidation_case WHERE npi = '1003000999'", Integer.class))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("[MAM-45] health answers UP without a token")
  void health() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  private void insertCase(String status) {
    jdbc.update(
        "INSERT INTO revalidation_case (public_id, npi, due_date, status, opened_at)"
            + " VALUES (?, '1003000999', ?, ?, now())",
        UUID.randomUUID(),
        Date.valueOf("2026-02-28"),
        status);
  }
}
