package gov.medicaid.pem.provider.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A test per role for GET /api/v1/providers/{npi} (x-roles: PROVIDER (own), ANALYST, SUPERVISOR,
 * ADMIN, SYSTEM).
 */
class ProviderLookupRolesTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc.update("DELETE FROM provider WHERE npi IN ('1005000102', '1003000111')");
    for (String npi : new String[] {"1005000102", "1003000111"}) {
      jdbc.update(
          "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, enroll_status, screen_flag,"
              + " effective_date, legal_name, dba_name, owner_npi)"
              + " VALUES (?, ?, '900000102', 'GR', '261QM0801X', 'P', 'U', DATE '2026-01-15', 'Synthetic Clinic Group',"
              + " 'Synthetic Clinic', '1990000999')",
          UUID.randomUUID(),
          npi);
    }
  }

  @ParameterizedTest(name = "{0} may read any provider")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "ADMIN", "SYSTEM"})
  @DisplayName("[MAM-34 AC-1] staff roles and SYSTEM read any provider")
  void staffRoles(String role) throws Exception {
    mvc.perform(get("/api/v1/providers/1005000102").with(PemJwt.user("x", null, role)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.npi").value("1005000102"))
        .andExpect(jsonPath("$.organization.legalName").value("Synthetic Clinic Group"))
        .andExpect(jsonPath("$.organization.ownerNpi").value("1990000999"))
        .andExpect(jsonPath("$.riskTier").doesNotExist());
  }

  @Test
  @DisplayName("[MAM-34 AC-2] a PROVIDER reads their own record")
  void providerOwn() throws Exception {
    mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.provider("1003000111")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.npi").value("1003000111"));
  }

  @Test
  @DisplayName("[MAM-34 AC-2] a PROVIDER gets 403 for another NPI")
  void providerOther() throws Exception {
    mvc.perform(get("/api/v1/providers/1005000102").with(PemJwt.provider("1003000111")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("[MAM-34 AC-2] a user with no PEM role gets 403")
  void noRole() throws Exception {
    mvc.perform(get("/api/v1/providers/1005000102").with(PemJwt.user("nobody", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("[MAM-34 AC-2] no token gives 401")
  void anonymous() throws Exception {
    mvc.perform(get("/api/v1/providers/1005000102")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("[MAM-34 AC-1] an unknown NPI gives a 404 problem")
  void unknown() throws Exception {
    mvc.perform(get("/api/v1/providers/1009999999").with(PemJwt.analyst()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("[MAM-34 AC-1] an NPI that is not 10 digits gives 400")
  void malformedNpi() throws Exception {
    mvc.perform(get("/api/v1/providers/12AB").with(PemJwt.analyst()))
        .andExpect(status().isBadRequest());
  }
}
