package gov.medicaid.pem.kit.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;

/**
 * Written after the code (BearerProblemHandler exists already): the 401 and the 403 that the
 * security filter chain itself answers. Spring's bearer handlers run first, so the status and the
 * WWW-Authenticate header are Spring's own; the body is an RFC 7807 problem with no stack trace and
 * no internal detail.
 */
class BearerProblemHandlerTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String SECRET = "internal-keycloak:8180 at gov.medicaid.pem.SecretClass";

  private final BearerProblemHandler handler = new BearerProblemHandler();

  private static MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/api/v1/providers/1003000111");
  }

  /** A request made with a valid bearer token: the principal is the JWT authentication. */
  private static MockHttpServletRequest requestWithToken() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject("u-1")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.MAX)
            .build();
    MockHttpServletRequest request = request();
    request.setUserPrincipal(new JwtAuthenticationToken(jwt));
    return request;
  }

  private MockHttpServletResponse unauthorized(AuthenticationException ex) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.commence(request(), response, ex);
    return response;
  }

  private MockHttpServletResponse forbidden(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.handle(request, response, new AccessDeniedException(SECRET));
    return response;
  }

  private static JsonNode body(MockHttpServletResponse response) throws Exception {
    return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
  }

  // ---- 401: no token or an unusable token -------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-26 AC-2] a request without a token gets 401 with the WWW-Authenticate: Bearer header")
  void noTokenIs401WithBearerChallenge() throws Exception {
    MockHttpServletResponse response =
        unauthorized(new InsufficientAuthenticationException("Full authentication is required"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the 401 body is application/problem+json with title Unauthorized, status 401 and a short detail")
  void noTokenBodyIsAProblem() throws Exception {
    MockHttpServletResponse response =
        unauthorized(new InsufficientAuthenticationException("Full authentication is required"));

    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode problem = body(response);
    assertThat(problem.path("title").asText()).isEqualTo("Unauthorized");
    assertThat(problem.path("status").asInt()).isEqualTo(401);
    assertThat(problem.path("detail").asText()).isNotBlank().hasSizeLessThanOrEqualTo(100);
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] an invalid token keeps Spring's own challenge (error invalid_token) and the body does not repeat the reason")
  void invalidTokenKeepsSpringChallengeAndHidesTheReason() throws Exception {
    var invalid = new InvalidBearerTokenException("Jwt expired at 2026-01-01 on " + SECRET);
    MockHttpServletResponse spring = new MockHttpServletResponse();
    new BearerTokenAuthenticationEntryPoint().commence(request(), spring, invalid);

    MockHttpServletResponse response = unauthorized(invalid);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .isEqualTo(spring.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .contains("error=\"invalid_token\"");
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .isNotBlank()
        .doesNotContain("Jwt expired", "internal-keycloak", "SecretClass");
  }

  // ---- 403: a valid token without the needed role
  // ------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-26 AC-2] a valid token without the needed role gets 403 with Spring's WWW-Authenticate value")
  void missingRoleIs403WithSpringChallenge() throws Exception {
    MockHttpServletResponse spring = new MockHttpServletResponse();
    new BearerTokenAccessDeniedHandler()
        .handle(requestWithToken(), spring, new AccessDeniedException("x"));

    MockHttpServletResponse response = forbidden(requestWithToken());

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .isEqualTo(spring.getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .contains("Bearer")
        .contains("error=\"insufficient_scope\"");
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] the 403 body is application/problem+json with title Forbidden, status 403 and a short detail")
  void missingRoleBodyIsAProblem() throws Exception {
    MockHttpServletResponse response = forbidden(requestWithToken());

    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode problem = body(response);
    assertThat(problem.path("title").asText()).isEqualTo("Forbidden");
    assertThat(problem.path("status").asInt()).isEqualTo(403);
    assertThat(problem.path("detail").asText()).isNotBlank().hasSizeLessThanOrEqualTo(100);
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-2] a denied request that carries no bearer token principal still gets a 403 problem and Spring's plain Bearer challenge")
  void deniedWithoutTokenPrincipalIs403() throws Exception {
    MockHttpServletResponse response = forbidden(request());

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    assertThat(response.getContentType()).startsWith("application/problem+json");
  }

  // ---- neither body leaks anything --------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"401", "403"})
  @DisplayName(
      "[MAM-26 AC-2] neither body carries a stack trace or an internal detail: only the problem fields, with no exception text")
  void bodiesHaveOnlyTheProblemFields(String status) throws Exception {
    MockHttpServletResponse response =
        status.equals("401")
            ? unauthorized(new InvalidBearerTokenException(SECRET))
            : forbidden(requestWithToken());

    JsonNode problem = body(response);
    List<String> fields = new ArrayList<>();
    problem.fieldNames().forEachRemaining(fields::add);
    assertThat(fields)
        .contains("title", "status", "detail")
        .isSubsetOf("type", "title", "status", "detail", "instance");
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain(
            "internal-keycloak",
            "SecretClass",
            "Exception",
            "Caused by",
            "stackTrace",
            "org.springframework",
            "gov.medicaid");
  }
}
