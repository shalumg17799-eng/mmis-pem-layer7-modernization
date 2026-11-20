package gov.medicaid.pem.apigateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import gov.medicaid.pem.kit.web.CorrelationId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The route table: each path family of the OpenAPI contracts goes to the service that owns it. The
 * bearer token is passed on unchanged (the services decide on roles); the correlation id is
 * forwarded; an unreachable service answers 503 problem details without internal addresses.
 */
@Configuration
public class PemRoutes {

  /** service -> the path patterns it owns (modern/contracts/openapi/[service]-service.yaml). */
  static final Map<String, List<String>> OWNERS = new LinkedHashMap<>();

  static {
    OWNERS.put("provider", List.of("/api/v1/providers/**", "/api/v1/legacy/**"));
    OWNERS.put("enrollment", List.of("/api/v1/applications/**", "/api/v1/batch/intake-runs/**"));
    OWNERS.put("screening", List.of("/api/v1/screenings/**", "/api/v1/batch/screening-runs/**"));
    OWNERS.put("risk", List.of("/api/v1/risk-rules/**", "/api/v1/risk-assessments/**"));
    OWNERS.put(
        "revalidation",
        List.of("/api/v1/revalidations/**", "/api/v1/batch/revalidation-sweeps/**"));
    OWNERS.put(
        "sanctions", List.of("/api/v1/sanctions/**", "/api/v1/batch/sanction-intake-runs/**"));
    OWNERS.put("notification", List.of("/api/v1/notices/**", "/api/v1/notice-templates/**"));
    OWNERS.put(
        "directory",
        List.of("/api/v1/directory/**", "/fhir/**", "/api/v1/batch/directory-extracts/**"));
    OWNERS.put("tmsis", List.of("/api/v1/batch/tmsis-extracts/**"));
  }

  @Bean
  public RouterFunction<ServerResponse> pemRouterFunction(GatewayProperties props) {
    RouterFunction<ServerResponse> all = null;
    for (var e : OWNERS.entrySet()) {
      String service = e.getKey();
      RequestPredicate paths =
          e.getValue().stream()
              .map(RequestPredicates::path)
              .reduce(RequestPredicate::or)
              .orElseThrow();
      RouterFunction<ServerResponse> r =
          route(service + "-service")
              .route(paths, http())
              .before(uri(props.url(service)))
              .before(PemRoutes::forwardCorrelationId)
              .onError(ex -> true, (ex, req) -> unavailable(service))
              .build();
      all = all == null ? r : all.and(r);
    }
    return all;
  }

  static ServerRequest forwardCorrelationId(ServerRequest request) {
    String id = CorrelationId.currentOrNew();
    return ServerRequest.from(request).headers(h -> h.set(CorrelationId.HEADER, id)).build();
  }

  static ServerResponse unavailable(String service) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The " + service + " service is not available. Try again later.");
    p.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
    p.setProperty("correlationId", CorrelationId.currentOrNew());
    return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(p);
  }

  /** CORS for the web UI only (plt-gateway AC-4). */
  @Bean
  public CorsFilter pemCorsFilter(GatewayProperties props) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(props.corsOrigins());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationId.HEADER));
    c.setExposedHeaders(List.of(CorrelationId.HEADER, "Location", "Content-Disposition"));
    c.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return new CorsFilter(source);
  }
}
