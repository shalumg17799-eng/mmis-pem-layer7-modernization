package gov.medicaid.pem.notification.acceptance;

import gov.medicaid.pem.notification.TestMail;
import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** One Spring context (Postgres, Kafka, Mailpit) for all notification-service scenarios. */
@CucumberContextConfiguration
@SpringBootTest(properties = {"spring.config.name=notification-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
public class CucumberSpringConfig {

  @DynamicPropertySource
  static void sources(DynamicPropertyRegistry r) {
    TestMail.register(r);
  }
}
