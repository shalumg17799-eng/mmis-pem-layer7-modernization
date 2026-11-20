package gov.medicaid.pem.kit.config;

import gov.medicaid.pem.kit.web.CorrelationIdFilter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Correlation ids on every request and the injectable Clock (no LocalDate.now() in business logic).
 */
@AutoConfiguration
@EnableConfigurationProperties(PemKitProperties.class)
public class PemWebAutoConfiguration {

  @Bean
  public FilterRegistrationBean<CorrelationIdFilter> pemCorrelationIdFilter(
      @org.springframework.beans.factory.annotation.Value("${spring.application.name:}")
          String service) {
    var reg =
        new FilterRegistrationBean<>(new CorrelationIdFilter(service.isBlank() ? null : service));
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return reg;
  }

  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
