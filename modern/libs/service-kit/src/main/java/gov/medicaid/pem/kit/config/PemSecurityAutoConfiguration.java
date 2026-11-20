package gov.medicaid.pem.kit.config;

import gov.medicaid.pem.kit.security.BearerProblemHandler;
import gov.medicaid.pem.kit.security.KeycloakJwtConverter;
import gov.medicaid.pem.kit.web.ProblemHandler;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every service is an OAuth2 resource server (ADR-007): keys from Keycloak's JWKS endpoint, issuer
 * checked, realm roles as authorities, method-level checks with {@code @PreAuthorize}. Health, info
 * and the OpenAPI pages are open; a service can open more paths with {@code
 * pem.security.public-paths} (the public directory and FHIR API).
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@ConditionalOnProperty(prefix = "pem.security", name = "enabled", matchIfMissing = true)
@EnableMethodSecurity
public class PemSecurityAutoConfiguration {

  private static final List<String> OPEN =
      List.of(
          "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error");

  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder jwtDecoder(PemKitProperties props) {
    var s = props.getSecurity();
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(s.getJwksUri()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(s.getIssuer()));
    return decoder;
  }

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain pemSecurityFilterChain(HttpSecurity http, PemKitProperties props)
      throws Exception {
    List<String> open = new ArrayList<>(OPEN);
    open.addAll(props.getSecurity().getPublicPaths());
    var problems = new BearerProblemHandler();
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(open.toArray(String[]::new))
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtConverter()))
                    .authenticationEntryPoint(problems)
                    .accessDeniedHandler(problems));
    return http.build();
  }

  @Bean
  public ProblemHandler pemProblemHandler() {
    return new ProblemHandler();
  }
}
