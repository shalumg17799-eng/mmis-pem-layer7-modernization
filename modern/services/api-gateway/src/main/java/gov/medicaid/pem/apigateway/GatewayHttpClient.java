package gov.medicaid.pem.apigateway;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.autoconfigure.http.client.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The HTTP client behind the proxy routes: Apache HttpClient 5, chosen with {@code
 * spring.http.client.factory} in api-gateway.yml (ADR-013). It is set up as a plain relay, so that
 * what a service answers is what the caller gets, and a call reaches a service once:
 *
 * <ul>
 *   <li>no automatic retry: Apache repeats a PUT or DELETE after an I/O error and a call that was
 *       answered 429 or 503; the gateway must not repeat a write, and relays a service's own answer
 *       once (a repeated PUT of the risk rules would create a second version);
 *   <li>no content compression: Apache would add an {@code Accept-Encoding} header and unpack the
 *       answer, so the body and headers a service sent would not be the ones the caller gets;
 *   <li>no cookie handling: a cookie of one service must never be sent to another;
 *   <li>a pool as large as the number of calls that can be in flight (the requests are served by
 *       virtual threads, so there is no thread limit): Apache's default of 5 connections per
 *       service and 25 in all would make a few long downloads hold up every other call.
 * </ul>
 *
 * The connect and read limits are the Boot properties {@code spring.http.client.connect-timeout}
 * and {@code read-timeout}; Apache applies the read limit to each read of the socket, so a download
 * that keeps flowing is not cut off and one that stalls ends after the limit.
 */
@Configuration
public class GatewayHttpClient {

  /** Calls in flight to all services together, and to any one of them. */
  static final int MAX_CONNECTIONS = 200;

  @Bean
  ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder>
      relayHttpClient() {
    return builder ->
        builder
            .withHttpClientCustomizer(GatewayHttpClient::relayOnly)
            .withConnectionManagerCustomizer(GatewayHttpClient::sizePool);
  }

  private static void relayOnly(HttpClientBuilder client) {
    client.disableAutomaticRetries().disableContentCompression().disableCookieManagement();
  }

  private static void sizePool(PoolingHttpClientConnectionManagerBuilder pool) {
    pool.setMaxConnTotal(MAX_CONNECTIONS).setMaxConnPerRoute(MAX_CONNECTIONS);
  }
}
