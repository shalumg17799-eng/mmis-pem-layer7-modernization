package gov.medicaid.pem.tmsis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.medicaid.pem.testsupport.PemArchitecture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Platform checks of tmsis-service, written with its Lane 3 tests (MAM-71, MAM-75, MAM-78). */
class PlatformChecksTest extends IntegrationTest {

  @Test
  @DisplayName(
      "[MAM-71] tmsis-service respects the layering rules (domain, application, api, infrastructure)")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.tmsis");
  }

  @Test
  @DisplayName(
      "[MAM-75] Flyway creates the run, row, archive, outbox, processed-event and Spring Batch tables")
  void schema() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
            String.class);
    assertThat(tables)
        .contains(
            "extract_run",
            "extract_row",
            "extract_archive",
            "outbox_event",
            "processed_event",
            "batch_job_instance",
            "batch_job_execution",
            "batch_step_execution");
  }

  @Test
  @DisplayName("[MAM-75] health answers UP without a token")
  void health() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] the extract endpoints are not public: a call without a token is a 401")
  void extractEndpointsNeedAToken() throws Exception {
    mvc.perform(get(TmsisApi.BASE)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "[MAM-75] the outbox accepts a run id as a subject: the column is wider than an NPI (V3)")
  void outboxSubjectHoldsARunId() {
    assertThat(
            jdbc.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns"
                    + " WHERE table_name = 'outbox_event' AND column_name = 'subject'",
                Integer.class))
        .isGreaterThanOrEqualTo(36);
  }

  @Test
  @DisplayName("[MAM-78] the run table keeps the count of future enrolments (V4)")
  void futureEnrolmentColumn() {
    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'extract_run'",
                String.class))
        .contains("future_enrolment_count", "failure_reason", "row_count", "period_yyddd");
  }
}
