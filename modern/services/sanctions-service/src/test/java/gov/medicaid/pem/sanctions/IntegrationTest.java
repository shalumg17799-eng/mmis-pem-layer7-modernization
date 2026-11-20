package gov.medicaid.pem.sanctions;

import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the integration tests of sanctions-service: the real application with Postgres, Kafka
 * and the WireMock of TestProviderService that plays provider-service.
 */
@SpringBootTest(properties = {"spring.config.name=sanctions-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
public abstract class IntegrationTest {

  @Autowired private JdbcTemplate batchJdbc;

  @DynamicPropertySource
  static void sources(DynamicPropertyRegistry r) {
    TestProviderService.register(r);
  }

  /**
   * An intake run goes on in a background thread of the service. No test leaves one running into
   * the clean-up of the next test.
   */
  @AfterEach
  protected void backgroundJobsHaveEnded() {
    Awaitility.await("no sanction intake job is still running")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                batchJdbc.queryForObject(
                        "SELECT count(*) FROM batch_job_execution"
                            + " WHERE status IN ('STARTING', 'STARTED')",
                        Integer.class)
                    == 0);
  }
}
