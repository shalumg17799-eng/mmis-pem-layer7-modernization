package gov.medicaid.pem.kit.client;

import gov.medicaid.pem.kit.web.CorrelationId;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds REST clients for calls to other PEM services: SYSTEM bearer token plus the current
 * correlation id. Every client has a connect and a read timeout, so a service that does not answer
 * ends the call with a {@code ResourceAccessException} instead of holding the caller for ever. The
 * limits come from {@code pem.client.connect-timeout} and {@code pem.client.read-timeout}; one
 * client may use its own single limit ({@link #create(String, Duration)}).
 */
public class ServiceClients {

  /** Default limit for opening the connection to another service. */
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);

  /** Default limit for the answer of another service. */
  public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

  private final ServiceTokenProvider tokens;
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private final BiFunction<Duration, Duration, RestClient.Builder> builders;

  public ServiceClients(ServiceTokenProvider tokens) {
    this(tokens, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }

  public ServiceClients(
      ServiceTokenProvider tokens, Duration connectTimeout, Duration readTimeout) {
    this(tokens, connectTimeout, readTimeout, ServiceClients::builder);
  }

  /**
   * The builder of the supplier is used as it is: its request factory, and so its limits, are the
   * caller's.
   */
  public ServiceClients(ServiceTokenProvider tokens, Supplier<RestClient.Builder> builders) {
    this(tokens, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, (connect, read) -> builders.get());
  }

  private ServiceClients(
      ServiceTokenProvider tokens,
      Duration connectTimeout,
      Duration readTimeout,
      BiFunction<Duration, Duration, RestClient.Builder> builders) {
    this.tokens = tokens;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.builders = builders;
  }

  /**
   * A builder whose requests give up connecting after {@code connectTimeout} and waiting for the
   * answer after {@code readTimeout}.
   */
  public static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    factory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(factory);
  }

  public RestClient create(String baseUrl) {
    return create(baseUrl, connectTimeout, readTimeout);
  }

  /** A client that uses {@code timeout} both for connecting and for the answer. */
  public RestClient create(String baseUrl, Duration timeout) {
    return create(baseUrl, timeout, timeout);
  }

  private RestClient create(String baseUrl, Duration connect, Duration read) {
    return builders
        .apply(connect, read)
        .baseUrl(baseUrl)
        .requestInterceptor(
            (request, body, execution) -> {
              request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
              request.getHeaders().set(CorrelationId.HEADER, CorrelationId.currentOrNew());
              return execution.execute(request, body);
            })
        .build();
  }
}
