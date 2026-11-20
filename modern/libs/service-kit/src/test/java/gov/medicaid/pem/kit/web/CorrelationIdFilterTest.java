package gov.medicaid.pem.kit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  @DisplayName("[MAM-36 AC-4] an incoming X-Correlation-Id is kept, logged (MDC) and echoed")
  void keepsTheIncomingId() throws Exception {
    var req = new MockHttpServletRequest("GET", "/api/v1/providers/1003000111");
    req.addHeader(CorrelationId.HEADER, "abc-123");
    var res = new MockHttpServletResponse();
    AtomicReference<String> seen = new AtomicReference<>();

    filter.doFilter(
        req,
        res,
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
            seen.set(MDC.get(CorrelationId.MDC_KEY));
          }
        });

    assertThat(seen.get()).isEqualTo("abc-123");
    assertThat(res.getHeader(CorrelationId.HEADER)).isEqualTo("abc-123");
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("[MAM-36 AC-4] a request without X-Correlation-Id gets a generated one")
  void generatesAnId() throws Exception {
    var res = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest("GET", "/x"), res, new MockFilterChain());
    assertThat(res.getHeader(CorrelationId.HEADER)).isNotBlank();
  }

  @Test
  @DisplayName("[MAM-36 AC-4] a hostile header value is replaced by a generated id")
  void rejectsUnsafeValues() throws Exception {
    var req = new MockHttpServletRequest("GET", "/x");
    req.addHeader(CorrelationId.HEADER, "bad\r\nvalue");
    var res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    assertThat(res.getHeader(CorrelationId.HEADER))
        .doesNotContain("\n")
        .isNotEqualTo("bad\r\nvalue");
  }
}
