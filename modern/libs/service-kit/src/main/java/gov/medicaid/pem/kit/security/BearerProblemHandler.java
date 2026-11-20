package gov.medicaid.pem.kit.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The 401 and 403 that the security filter chain itself answers (no token, an invalid token, a path
 * that needs another role) as RFC 7807 problem details, as every contract declares them. Spring's
 * bearer-token handlers still run first, so the status and the {@code WWW-Authenticate: Bearer}
 * header are exactly what Spring sets; only the empty body is replaced. The correlation id is on
 * the response already: its filter runs before the chain.
 */
public class BearerProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final AuthenticationEntryPoint bearerEntryPoint =
      new BearerTokenAuthenticationEntryPoint();
  private final AccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();
  private final MappingJackson2HttpMessageConverter json =
      new MappingJackson2HttpMessageConverter();

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException, ServletException {
    bearerEntryPoint.commence(request, response, ex);
    write(response, HttpStatus.UNAUTHORIZED, "A valid bearer token is required.");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException, ServletException {
    bearerDeniedHandler.handle(request, response, ex);
    write(response, HttpStatus.FORBIDDEN, "You are not allowed to do this.");
  }

  private void write(HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    json.write(
        problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
  }
}
