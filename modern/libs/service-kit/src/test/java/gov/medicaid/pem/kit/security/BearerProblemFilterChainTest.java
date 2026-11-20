package gov.medicaid.pem.kit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gov.medicaid.pem.kit.config.PemSecurityAutoConfiguration;
import gov.medicaid.pem.kit.config.PemWebAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.ConfigurableWebApplicationContext;

/**
 * Written after the code (BearerProblemHandler is wired already): the real security filter chain
 * that PemSecurityAutoConfiguration builds, with real signed JWTs checked by a decoder that trusts
 * one test key, answers 401 and 403 as problem+json. This is the wiring test: without {@code
 * authenticationEntryPoint(...)} and {@code accessDeniedHandler(...)} on the resource server, the
 * bodies would be empty.
 */
class BearerProblemFilterChainTest {

  private static final String ISSUER = "http://localhost:8180/realms/pem";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /**
   * Only ADMIN may use /actuator/** (PemSecurityAutoConfiguration); every other path needs a login.
   */
  private static final String ADMIN_ONLY = "/actuator/info";

  private static final String PROTECTED = "/api/v1/providers/1003000111";

  private static KeyPair realmKey;
  private static KeyPair strangerKey;

  @BeforeAll
  static void generateKeys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    realmKey = generator.generateKeyPair();
    strangerKey = generator.generateKeyPair();
  }

  private static JwtDecoder decoder() {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) realmKey.getPublic()).build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
    timestamps.setClock(CLOCK);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
    return decoder;
  }

  private static String token(KeyPair signer, Instant expiresAt, String... realmRoles)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("u-1")
            .issuer(ISSUER)
            .claim("preferred_username", "user1")
            .claim("realm_access", Map.of("roles", List.of(realmRoles)))
            .issueTime(Date.from(CLOCK.instant().minusSeconds(60)))
            .expirationTime(Date.from(expiresAt))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(signer.getPrivate()));
    return jwt.serialize();
  }

  private static String validToken(String... realmRoles) throws Exception {
    return token(realmKey, CLOCK.instant().plusSeconds(3600), realmRoles);
  }

  private interface MvcTest {
    void run(MockMvc mvc) throws Exception;
  }

  /** The real chain of PemSecurityAutoConfiguration, with the decoder of the test key. */
  private static void withChain(MvcTest test) {
    new WebApplicationContextRunner()
        .withPropertyValues(
            "spring.application.name=provider-service",
            "pem.security.issuer=" + ISSUER,
            "pem.security.jwks-uri=http://keycloak:8180/realms/pem/protocol/openid-connect/certs")
        .withBean(JwtDecoder.class, BearerProblemFilterChainTest::decoder)
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class,
                DispatcherServletAutoConfiguration.class,
                WebMvcAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                PemWebAutoConfiguration.class,
                PemSecurityAutoConfiguration.class))
        .run(
            ctx ->
                test.run(
                    MockMvcBuilders.webAppContextSetup(
                            ctx.getSourceApplicationContext(
                                ConfigurableWebApplicationContext.class))
                        .apply(springSecurity())
                        .build()));
  }

  private static JsonNode body(MvcResult result) throws Exception {
    return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  // ---- 401 --------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-26 AC-2] a protected path called without a token gets 401 with the WWW-Authenticate: Bearer header")
  void noTokenIs401WithBearerChallenge() {
    withChain(
        mvc -> {
          MvcResult result = mvc.perform(get(PROTECTED)).andReturn();

          assertThat(result.getResponse().getStatus()).isEqualTo(401);
          assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
              .isEqualTo("Bearer");
        });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the 401 of the chain has a problem+json body: title Unauthorized, status 401, a short detail")
  void noTokenBodyIsAProblem() {
    withChain(
        mvc -> {
          MvcResult result = mvc.perform(get(PROTECTED)).andReturn();

          assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
          JsonNode problem = body(result);
          assertThat(problem.path("title").asText()).isEqualTo("Unauthorized");
          assertThat(problem.path("status").asInt()).isEqualTo(401);
          assertThat(problem.path("detail").asText()).isNotBlank().hasSizeLessThanOrEqualTo(100);
        });
  }

  @ParameterizedTest(name = "Accept: {0}")
  @ValueSource(strings = {"application/json", "*/*", "text/html"})
  @DisplayName(
      "[MAM-26 AC-2] the 401 body is problem+json whatever the Accept header of the caller says")
  void problemWhateverTheAcceptHeader(String accept) {
    withChain(
        mvc -> {
          MvcResult result =
              mvc.perform(get(PROTECTED).header(HttpHeaders.ACCEPT, accept)).andReturn();

          assertThat(result.getResponse().getStatus()).isEqualTo(401);
          assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        });
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"not-a-jwt", "expired", "signed by another key"})
  @DisplayName(
      "[MAM-26 AC-2] a token that cannot be used (garbage, expired, wrong signature) gets 401 with Spring's invalid_token challenge and a problem+json body")
  void unusableTokenIs401(String kind) {
    withChain(
        mvc -> {
          String bearer =
              switch (kind) {
                case "expired" ->
                    token(realmKey, CLOCK.instant().minus(Duration.ofHours(2)), "ANALYST");
                case "signed by another key" ->
                    token(strangerKey, CLOCK.instant().plusSeconds(3600), "ANALYST");
                default -> "not-a-jwt";
              };

          MvcResult result =
              mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                  .andReturn();

          assertThat(result.getResponse().getStatus()).isEqualTo(401);
          assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
              .startsWith("Bearer")
              .contains("error=\"invalid_token\"");
          assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
          assertThat(body(result).path("title").asText()).isEqualTo("Unauthorized");
        });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the 401 body for an undecodable token does not say why (no decoder message, no key or issuer detail)")
  void unusableTokenBodyHidesTheReason() {
    withChain(
        mvc -> {
          MvcResult result =
              mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                  .andReturn();

          assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
              .isNotBlank()
              .doesNotContainIgnoringCase("jwt")
              .doesNotContainIgnoringCase("decode")
              .doesNotContain("localhost:8180")
              .doesNotContain("Exception");
        });
  }

  // ---- 403 --------------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"PROVIDER", "ANALYST", "SUPERVISOR"})
  @DisplayName(
      "[MAM-26 AC-2] a valid token without the ADMIN role gets 403 with Spring's insufficient_scope challenge on an admin-only path")
  void tokenWithoutTheRoleIs403(String role) {
    withChain(
        mvc -> {
          MvcResult result =
              mvc.perform(
                      get(ADMIN_ONLY)
                          .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(role)))
                  .andReturn();

          assertThat(result.getResponse().getStatus()).isEqualTo(403);
          assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
              .startsWith("Bearer")
              .contains("error=\"insufficient_scope\"");
        });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the 403 of the chain has a problem+json body: title Forbidden, status 403, a short detail")
  void tokenWithoutTheRoleBodyIsAProblem() {
    withChain(
        mvc -> {
          MvcResult result =
              mvc.perform(
                      get(ADMIN_ONLY)
                          .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("ANALYST")))
                  .andReturn();

          assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
          JsonNode problem = body(result);
          assertThat(problem.path("title").asText()).isEqualTo("Forbidden");
          assertThat(problem.path("status").asInt()).isEqualTo(403);
          assertThat(problem.path("detail").asText()).isNotBlank().hasSizeLessThanOrEqualTo(100);
        });
  }

  @Test
  @DisplayName("[MAM-26 AC-2] a valid token with no realm roles at all gets 403, not 401")
  void tokenWithNoRolesIs403() {
    withChain(
        mvc -> {
          MvcResult result =
              mvc.perform(
                      get(ADMIN_ONLY).header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                  .andReturn();

          assertThat(result.getResponse().getStatus()).isEqualTo(403);
        });
  }

  // ---- no leaks and positive controls
  // ------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"401 no token", "403 wrong role"})
  @DisplayName(
      "[MAM-26 AC-2] neither body of the chain carries a stack trace or an internal detail: only the problem fields")
  void bodiesHaveOnlyTheProblemFields(String kind) {
    withChain(
        mvc -> {
          MvcResult result =
              kind.startsWith("401")
                  ? mvc.perform(get(PROTECTED)).andReturn()
                  : mvc.perform(
                          get(ADMIN_ONLY)
                              .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("ANALYST")))
                      .andReturn();

          List<String> fields = new ArrayList<>();
          body(result).fieldNames().forEachRemaining(fields::add);
          assertThat(fields)
              .contains("title", "status", "detail")
              .isSubsetOf("type", "title", "status", "detail", "instance");
          assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
              .doesNotContain(
                  "Exception", "Caused by", "stackTrace", "org.springframework", "gov.medicaid");
        });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the denial is role-driven: an ADMIN token passes the admin-only path (neither 401 nor 403)")
  void adminPassesTheAdminOnlyPath() {
    withChain(
        mvc -> {
          MvcResult result =
              mvc.perform(
                      get(ADMIN_ONLY)
                          .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("ADMIN")))
                  .andReturn();

          assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
        });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the 401 is not a blanket: the open health path needs no token (no 401)")
  void healthPathIsOpen() {
    withChain(
        mvc -> {
          MvcResult result = mvc.perform(get("/actuator/health")).andReturn();

          assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
        });
  }
}
