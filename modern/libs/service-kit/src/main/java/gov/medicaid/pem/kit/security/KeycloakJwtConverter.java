package gov.medicaid.pem.kit.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Keycloak realm roles ({@code realm_access.roles}) become {@code ROLE_<role>} authorities
 * (ADR-007); the principal name is {@code preferred_username}, or {@code sub} when there is none
 * (service accounts).
 */
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String name =
        jwt.hasClaim("preferred_username")
            ? jwt.getClaimAsString("preferred_username")
            : jwt.getSubject();
    return new JwtAuthenticationToken(jwt, authorities(jwt), name);
  }

  static Collection<GrantedAuthority> authorities(Jwt jwt) {
    Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
    if (realm == null || !(realm.get("roles") instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
        .toList();
  }
}
