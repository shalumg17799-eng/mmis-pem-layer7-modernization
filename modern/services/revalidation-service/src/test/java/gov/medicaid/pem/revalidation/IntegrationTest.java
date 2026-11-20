package gov.medicaid.pem.revalidation;

import gov.medicaid.pem.testsupport.PemContainers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Base for integration tests: the real application with Postgres and Kafka from Testcontainers (one
 * shared context).
 */
@SpringBootTest(properties = {"spring.config.name=revalidation-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
public abstract class IntegrationTest {}
