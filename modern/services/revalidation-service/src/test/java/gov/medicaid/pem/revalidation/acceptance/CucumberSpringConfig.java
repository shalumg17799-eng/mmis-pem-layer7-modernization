package gov.medicaid.pem.revalidation.acceptance;

import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/** One Spring context (Postgres and Kafka) for all revalidation-service scenarios. */
@CucumberContextConfiguration
@SpringBootTest(properties = {"spring.config.name=revalidation-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
public class CucumberSpringConfig {}
