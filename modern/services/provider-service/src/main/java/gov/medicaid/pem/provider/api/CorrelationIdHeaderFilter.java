package gov.medicaid.pem.provider.api;

import gov.medicaid.pem.kit.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * An X-Correlation-Id that is not safe to log (a line break, a forged log line) is replaced, not
 * refused (MAM-27 AC-2, MAM-37 AC-8). The kit's filter has already put the safe id (the caller's,
 * or a generated uuid) in the log context and in the answer; the security firewall would refuse a
 * request with 400 as soon as the body reader looks at the unsafe header, so from here on the
 * request shows the safe id for this one header and the unsafe text goes no further.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdHeaderFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String sent = request.getHeader(CorrelationId.HEADER);
    String safe = MDC.get(CorrelationId.MDC_KEY);
    if (sent != null && safe != null && !sent.equals(safe)) {
      chain.doFilter(new SafeCorrelationId(request, safe), response);
    } else {
      chain.doFilter(request, response);
    }
  }

  /** The request, with the safe id as the only value of X-Correlation-Id. */
  private static final class SafeCorrelationId extends HttpServletRequestWrapper {

    private final String safe;

    SafeCorrelationId(HttpServletRequest request, String safe) {
      super(request);
      this.safe = safe;
    }

    @Override
    public String getHeader(String name) {
      return isCorrelationId(name) ? safe : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isCorrelationId(name)
          ? Collections.enumeration(List.of(safe))
          : super.getHeaders(name);
    }

    private static boolean isCorrelationId(String name) {
      return CorrelationId.HEADER.equalsIgnoreCase(name);
    }
  }
}
