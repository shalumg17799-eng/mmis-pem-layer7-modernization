package gov.medicaid.pem.risk;

import gov.medicaid.pem.testsupport.HealthOutage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MAM-36 AC-5 on risk-service (Q-099 answer B: verified per service with the test's own database).
 */
class HealthOutageTest extends IntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName(
      "[MAM-36 AC-5] risk-service: with its database down health answers 503 DOWN, and 200 UP again once it is back")
  void healthFollowsTheDatabase() throws Exception {
    HealthOutage.assertVisibleAndRecovers(mvc);
  }
}
