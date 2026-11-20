package gov.medicaid.pem.provider.acceptance;

import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * One Spring context (real Postgres and Kafka from Testcontainers) for all provider-service
 * scenarios.
 */
@CucumberContextConfiguration
@SpringBootTest(properties = {"spring.config.name=provider-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
public class CucumberSpringConfig {}
