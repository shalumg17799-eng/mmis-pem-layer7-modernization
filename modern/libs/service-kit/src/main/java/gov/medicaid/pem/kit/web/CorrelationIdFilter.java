package gov.medicaid.pem.kit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts or generates X-Correlation-Id, puts it in the MDC for the request, and echoes it on the
 * response.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  /**
   * Not "service": the ECS log format writes its own "service" object, and an MDC entry of the same
   * name made every log line of a request fail to append ("The name 'service' has already been
   * written"), so request-thread log lines were lost.
   */
  public static final String SERVICE_MDC_KEY = "pemService";

  private final String service;

  public CorrelationIdFilter() {
    this(null);
  }

  public CorrelationIdFilter(String service) {
    this.service = service;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = CorrelationId.sanitize(request.getHeader(CorrelationId.HEADER));
    MDC.put(CorrelationId.MDC_KEY, id);
    if (service != null) {
      MDC.put(SERVICE_MDC_KEY, service);
    }
    response.setHeader(CorrelationId.HEADER, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
      MDC.remove(SERVICE_MDC_KEY);
    }
  }
}
