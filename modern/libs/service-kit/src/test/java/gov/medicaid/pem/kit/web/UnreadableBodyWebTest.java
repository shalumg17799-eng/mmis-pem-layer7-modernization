package gov.medicaid.pem.kit.web;

import static gov.medicaid.pem.kit.web.ProblemHandlerUnreadableTest.PARSER_WORDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gov.medicaid.pem.kit.config.PemSecurityAutoConfiguration;
import gov.medicaid.pem.kit.config.PemWebAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.ConfigurableWebApplicationContext;

/**
 * Written after the code (handleUnreadable exists already): a real request with a body that cannot
 * be read, through the auto-configured ProblemHandler of a kit service, ends in a 400
 * application/problem+json that says nothing about the parser; and a service's own advice for the
 * same exception (as directory-service and tmsis-service have, MAM-67) still wins.
 */
class UnreadableBodyWebTest {

  /** A service endpoint that reads a JSON object. */
  @RestController
  static class Probe {

    record Body(String name, int count) {}

    @PostMapping("/probe")
    Map<String, Object> accept(@RequestBody Body body) {
      return Map.of("name", body.name(), "count", body.count());
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ProbeConfig {
    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  /** The shape of the advice a service has: lowest precedence, its own detail. */
  @RestControllerAdvice
  @Order(Ordered.LOWEST_PRECEDENCE)
  static class OwnAdvice {

    static final String DETAIL =
        "The request body is not valid: businessDate must be an ISO date (yyyy-mm-dd).";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable() {
      ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETAIL);
      p.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
      return ResponseEntity.badRequest().body(p);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnAdviceConfig {
    @Bean
    OwnAdvice ownAdvice() {
      return new OwnAdvice();
    }
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private interface MvcTest {
    void run(MockMvc mvc) throws Exception;
  }

  /**
   * The auto-configuration of a kit service (the same list as PemKitAutoConfigurationTest). A
   * service's own beans are registered before the auto-configured ProblemHandler, as in a real
   * service.
   */
  private static void withService(MvcTest test, Class<?>... serviceConfiguration) {
    new WebApplicationContextRunner()
        .withPropertyValues(
            "spring.application.name=directory-service",
            "pem.security.issuer=http://localhost:8180/realms/pem",
            "pem.security.jwks-uri=http://keycloak:8180/realms/pem/protocol/openid-connect/certs")
        .withUserConfiguration(serviceConfiguration)
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
                        .build()));
  }

  private static MvcResult postJson(MockMvc mvc, String body) throws Exception {
    return mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn();
  }

  private static String text(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  /**
   * A body that cannot be read, and a piece of it (or of Spring's message) that must not come back.
   */
  static Stream<Arguments> unreadableBodies() {
    return Stream.of(
        Arguments.of("not JSON", "this is not json {", "this is not json"),
        Arguments.of("wrong type", "{\"name\":\"x\",\"count\":\"many\"}", "many"),
        Arguments.of("JSON array", "[{\"name\":\"x\",\"count\":1}]", "[{"),
        Arguments.of("truncated JSON", "{\"name\":\"x\",\"count\":", "\"count\""),
        Arguments.of("a JSON string", "\"hello\"", "hello"),
        Arguments.of("no body at all", "", "Required request body"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableBodies")
  @DisplayName(
      "[MAM-29 AC-6] an unreadable JSON body (not JSON, wrong type, an array, truncated, missing) gets 400 as application/problem+json")
  void unreadableBodyIs400ProblemJson(String name, String body, String echo) {
    withService(
        mvc -> {
          MvcResult result = postJson(mvc, body);

          assertThat(result.getResponse().getStatus()).isEqualTo(400);
          assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        },
        ProbeConfig.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableBodies")
  @DisplayName(
      "[MAM-29 AC-6] the problem of an unreadable body has the title Bad Request, status 400 and a detail")
  void unreadableBodyProblemFields(String name, String body, String echo) {
    withService(
        mvc -> {
          JsonNode problem = JSON.readTree(text(postJson(mvc, body)));

          assertThat(problem.path("title").asText()).isEqualTo("Bad Request");
          assertThat(problem.path("status").asInt()).isEqualTo(400);
          assertThat(problem.path("detail").asText()).isNotBlank();
        },
        ProbeConfig.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableBodies")
  @DisplayName(
      "[MAM-29 AC-6] the answer for an unreadable body says nothing about the parser and does not echo the body")
  void unreadableBodyLeaksNothing(String name, String body, String echo) {
    withService(
        mvc -> {
          String answer = text(postJson(mvc, body));

          assertThat(answer).doesNotContain(PARSER_WORDS);
          assertThat(answer).doesNotContain(echo);
        },
        ProbeConfig.class);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] a caller that asks for application/json still gets the problem as application/problem+json")
  void acceptJsonGetsProblemJson() {
    withService(
        mvc -> {
          MvcResult result =
              mvc.perform(
                      post("/probe")
                          .contentType(MediaType.APPLICATION_JSON)
                          .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                          .content("not json"))
                  .andReturn();

          assertThat(result.getResponse().getStatus()).isEqualTo(400);
          assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        },
        ProbeConfig.class);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the 400 is specific to an unreadable body: a readable JSON object is accepted (200)")
  void readableBodyIsAccepted() {
    withService(
        mvc -> {
          MvcResult result = postJson(mvc, "{\"name\":\"x\",\"count\":3}");

          assertThat(result.getResponse().getStatus()).isEqualTo(200);
        },
        ProbeConfig.class);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] a service's own advice for HttpMessageNotReadableException still wins over the kit's answer")
  void ownAdviceWins() {
    withService(
        mvc -> {
          MvcResult result = postJson(mvc, "not json");

          assertThat(result.getResponse().getStatus()).isEqualTo(400);
          assertThat(JSON.readTree(text(result)).path("detail").asText())
              .isEqualTo(OwnAdvice.DETAIL);
        },
        ProbeConfig.class,
        OwnAdviceConfig.class);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] without an advice of its own a service gets the kit's detail, so the own-advice test tells the two apart")
  void kitDetailWhenNoOwnAdvice() {
    withService(
        mvc -> {
          assertThat(JSON.readTree(text(postJson(mvc, "not json"))).path("detail").asText())
              .isNotBlank()
              .isNotEqualTo(OwnAdvice.DETAIL);
        },
        ProbeConfig.class);
  }
}
