package gov.medicaid.pem.notification;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.testsupport.PemArchitecture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Platform checks of notification-service, written with its first story (MAM-59). */
class PlatformChecksTest extends IntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[MAM-59] notification-service respects the layering rules")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.notification");
  }

  @Test
  @DisplayName("[MAM-59] the notice templates carry the names of the Q-067 label table as subjects")
  void templates() {
    assertThat(
            jdbc.queryForList("SELECT code || '=' || subject FROM notice_template", String.class))
        .contains(
            "APPROVED=Enrolment approved",
            "DENIED=Enrolment denied",
            "REJECTED=Application rejected",
            "REVALIDATION_DUE=Revalidation due");
  }
}
