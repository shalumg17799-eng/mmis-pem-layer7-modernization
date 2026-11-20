package gov.medicaid.pem.kit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityKitTest {

  private static Jwt jwt(Map<String, Object> claims) {
    var b =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.MAX);
    claims.forEach(b::claim);
    return b.build();
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] Keycloak realm roles become ROLE_ authorities and the username is the principal name")
  void realmRolesBecomeAuthorities() {
    Jwt jwt =
        jwt(
            Map.of(
                "sub",
                "u-1",
                "preferred_username",
                "analyst1",
                "realm_access",
                Map.of("roles", List.of("ANALYST", "SUPERVISOR"))));

    JwtAuthenticationToken auth = (JwtAuthenticationToken) new KeycloakJwtConverter().convert(jwt);

    assertThat(auth.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ANALYST", "ROLE_SUPERVISOR");
    assertThat(auth.getName()).isEqualTo("analyst1");
  }

  @Test
  @DisplayName("[MAM-26 AC-2] a token without realm roles has no authorities")
  void noRoles() {
    var auth = new KeycloakJwtConverter().convert(jwt(Map.of("sub", "u-2")));
    assertThat(auth.getAuthorities()).isEmpty();
    assertThat(auth.getName()).isEqualTo("u-2");
  }

  @Test
  @DisplayName("[MAM-34 AC-2] a PROVIDER may read only the NPI in their npi claim")
  void providerSeesOnlyOwnNpi() {
    var auth =
        new KeycloakJwtConverter()
            .convert(
                jwt(
                    Map.of(
                        "sub",
                        "p",
                        "npi",
                        "1003000111",
                        "realm_access",
                        Map.of("roles", List.of("PROVIDER")))));
    var user = CurrentUser.of(auth);

    assertThat(user.npi()).contains("1003000111");
    assertThat(user.hasRole("PROVIDER")).isTrue();
    user.requireAccessToNpi("1003000111");
    assertThatThrownBy(() -> user.requireAccessToNpi("1003000222"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("[MAM-34 AC-1] staff roles may read any NPI")
  void staffSeeAll() {
    var auth =
        new KeycloakJwtConverter()
            .convert(jwt(Map.of("sub", "a", "realm_access", Map.of("roles", List.of("ANALYST")))));
    CurrentUser.of(auth).requireAccessToNpi("1003000222");
  }
}
