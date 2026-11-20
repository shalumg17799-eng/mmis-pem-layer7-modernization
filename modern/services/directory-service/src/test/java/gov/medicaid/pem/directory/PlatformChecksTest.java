package gov.medicaid.pem.directory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.medicaid.pem.testsupport.PemArchitecture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Platform checks of directory-service, written with its first story (MAM-61). */
class PlatformChecksTest extends IntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("[MAM-61] directory-service respects the layering rules")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.directory");
  }

  @Test
  @DisplayName("[MAM-61] the public directory answers without a token")
  void publicDirectory() throws Exception {
    mvc.perform(get("/api/v1/directory/providers")).andExpect(status().isOk());
  }
}
