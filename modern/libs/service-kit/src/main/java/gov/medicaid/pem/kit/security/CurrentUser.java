package gov.medicaid.pem.kit.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The signed-in user. A PROVIDER may only see the NPI held in the {@code npi} claim of their token
 * (the Keycloak user attribute); ANALYST, SUPERVISOR, ADMIN and SYSTEM may see every provider
 * (D-023, ADR-007).
 */
public record CurrentUser(String name, Set<String> roles, Optional<String> npi) {

  private static final Set<String> STAFF = Set.of("ANALYST", "SUPERVISOR", "ADMIN", "SYSTEM");

  public static CurrentUser of(Authentication auth) {
    Set<String> roles =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .collect(Collectors.toUnmodifiableSet());
    Optional<String> npi =
        auth instanceof JwtAuthenticationToken jwt
            ? Optional.ofNullable(jwt.getToken().getClaimAsString("npi"))
            : Optional.empty();
    return new CurrentUser(auth.getName(), roles, npi);
  }

  public static CurrentUser current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      throw new AccessDeniedException("not signed in");
    }
    return of(auth);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean isStaff() {
    return roles.stream().anyMatch(STAFF::contains);
  }

  /** Throws AccessDeniedException unless the user may see this provider. */
  public void requireAccessToNpi(String providerNpi) {
    if (isStaff()) {
      return;
    }
    if (hasRole("PROVIDER") && npi.isPresent() && npi.get().equals(providerNpi)) {
      return;
    }
    throw new AccessDeniedException("provider " + providerNpi + " is not yours");
  }
}
