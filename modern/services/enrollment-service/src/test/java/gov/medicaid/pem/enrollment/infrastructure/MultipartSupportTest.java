package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.core.StandardContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

/**
 * MAM-56 AC-1: the legacy file arrives as multipart/form-data, and Tomcat only parses the parts of
 * a request when the servlet context allows it (the local run mode has no multipart configuration,
 * ADR-011). MockMvc does not go through Tomcat, so this is checked on Tomcat's own factory.
 */
class MultipartSupportTest {

  @Test
  @DisplayName("[MAM-56 AC-1] Tomcat does not parse multipart parts by itself")
  void tomcatDefault() {
    assertThat(new StandardContext().getAllowCasualMultipartParsing()).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-1] the web server of the service is allowed to parse the parts of the file upload")
  void partsAreParsed() {
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
    new MultipartSupport().multipartParsing().customize(factory);
    StandardContext context = new StandardContext();
    factory.getTomcatContextCustomizers().forEach(c -> c.customize(context));
    assertThat(context.getAllowCasualMultipartParsing()).isTrue();
  }
}
