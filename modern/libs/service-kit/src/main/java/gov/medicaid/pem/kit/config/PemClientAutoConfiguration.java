package gov.medicaid.pem.kit.config;

import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.kit.client.ServiceTokenProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Service-to-service calls with a SYSTEM token (ADR-007), switched on when {@code
 * pem.client.token-uri} is set.
 */
@AutoConfiguration(after = PemWebAutoConfiguration.class)
@ConditionalOnProperty(prefix = "pem.client", name = "token-uri")
public class PemClientAutoConfiguration {

  @Bean
  public ServiceTokenProvider serviceTokenProvider(PemKitProperties props, Clock clock) {
    var c = props.getClient();
    return new ServiceTokenProvider(
        ServiceClients.builder(c.getConnectTimeout(), c.getReadTimeout()).build(),
        c.getTokenUri(),
        c.getClientId(),
        c.getClientSecret(),
        clock);
  }

  @Bean
  public ServiceClients serviceClients(ServiceTokenProvider tokens, PemKitProperties props) {
    var c = props.getClient();
    return new ServiceClients(tokens, c.getConnectTimeout(), c.getReadTimeout());
  }
}
