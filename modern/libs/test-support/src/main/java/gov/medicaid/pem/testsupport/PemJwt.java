package gov.medicaid.pem.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Signed-in users for MockMvc tests, shaped like the Keycloak tokens of realm pem (realm roles, npi
 * claim).
 */
public final class PemJwt {

  private PemJwt() {}

  public static JwtRequestPostProcessor user(String username, String npi, String... roles) {
    List<String> r = Arrays.asList(roles);
    return jwt()
        .jwt(
            j -> {
              j.subject("sub-" + username).claim("preferred_username", username);
              j.claim("realm_access", java.util.Map.of("roles", r));
              if (npi != null) {
                j.claim("npi", npi);
              }
            })
        .authorities(
            r.stream()
                .map(
                    x ->
                        (org.springframework.security.core.GrantedAuthority)
                            new SimpleGrantedAuthority("ROLE_" + x))
                .toList());
  }

  public static JwtRequestPostProcessor provider(String npi) {
    return user("provider-" + npi, npi, "PROVIDER");
  }

  public static JwtRequestPostProcessor analyst() {
    return user("analyst1", null, "ANALYST");
  }

  public static JwtRequestPostProcessor supervisor() {
    return user("supervisor1", null, "SUPERVISOR");
  }

  public static JwtRequestPostProcessor admin() {
    return user("admin1", null, "ADMIN");
  }

  public static JwtRequestPostProcessor system() {
    return user("service-account-pem-services", null, "SYSTEM");
  }
}
