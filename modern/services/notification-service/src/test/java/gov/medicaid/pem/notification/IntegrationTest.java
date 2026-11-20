package gov.medicaid.pem.notification;

import gov.medicaid.pem.testsupport.PemContainers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for integration tests: the real application with Postgres, Kafka and the Mailpit of
 * TestMail.
 */
@SpringBootTest(properties = {"spring.config.name=notification-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
public abstract class IntegrationTest {

  @DynamicPropertySource
  static void sources(DynamicPropertyRegistry r) {
    TestMail.register(r);
  }
}
