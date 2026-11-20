package gov.medicaid.pem.tmsis;

import gov.medicaid.pem.testsupport.HealthOutage;
import gov.medicaid.pem.testsupport.PemContainers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MAM-36 AC-5 on tmsis-service (Q-099 answer B: verified per service with the test's own database).
 */
@SpringBootTest(properties = {"spring.config.name=tmsis-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
class HealthOutageTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName(
      "[MAM-36 AC-5] tmsis-service: with its database down health answers 503 DOWN, and 200 UP again once it is back")
  void healthFollowsTheDatabase() throws Exception {
    HealthOutage.assertVisibleAndRecovers(mvc);
  }
}
