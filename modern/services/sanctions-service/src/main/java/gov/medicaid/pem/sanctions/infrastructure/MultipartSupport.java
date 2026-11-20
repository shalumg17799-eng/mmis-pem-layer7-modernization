package gov.medicaid.pem.sanctions.infrastructure;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The legacy-file upload (contract FileRunRequest, multipart/form-data) needs Tomcat to parse the
 * request parts. In the local run mode (ADR-011) the servlet had no multipart configuration, so a
 * file run failed with "Unable to process parts as no multi-part configuration has been provided";
 * Tomcat is allowed to parse parts for any request of this service.
 */
@Configuration
public class MultipartSupport {

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartParsing() {
    return factory ->
        factory.addContextCustomizers(context -> context.setAllowCasualMultipartParsing(true));
  }
}
