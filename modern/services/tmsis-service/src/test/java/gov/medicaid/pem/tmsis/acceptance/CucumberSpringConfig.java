package gov.medicaid.pem.tmsis.acceptance;

import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * One Spring context (Postgres, Kafka, the fake provider-service, the test Clock) for all
 * tmsis-service scenarios.
 */
@CucumberContextConfiguration
@SpringBootTest(properties = {"spring.config.name=tmsis-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
@Import(TestClock.Config.class)
public class CucumberSpringConfig {

  @DynamicPropertySource
  static void sources(DynamicPropertyRegistry r) {
    TestProviderService.register(r);
  }
}
