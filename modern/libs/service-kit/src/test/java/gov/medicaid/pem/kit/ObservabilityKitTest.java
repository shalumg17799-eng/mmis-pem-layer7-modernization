package gov.medicaid.pem.kit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.config.PemKafkaAutoConfiguration;
import gov.medicaid.pem.kit.events.CorrelationIdRecordInterceptor;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.mock.env.MockEnvironment;

/** MAM-36: the kit's share of health, JSON logs and correlation ids. */
class ObservabilityKitTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  private static ConsumerRecord<Object, Object> record(String value) {
    return new ConsumerRecord<>("pem.enrollment.events", 0, 0L, "1003000111", value);
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-4] a consumed event's correlationId is in the MDC while it is handled, and removed afterwards")
  @SuppressWarnings("unchecked")
  void eventCorrelationIdFollowsTheConsumer() {
    var interceptor = new CorrelationIdRecordInterceptor();
    var rec = record("{\"id\":\"e1\",\"correlationId\":\"mam36-corr-1\",\"data\":{}}");
    Consumer<Object, Object> consumer = mock(Consumer.class);
    assertThat(interceptor.intercept(rec, consumer)).isSameAs(rec);
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("mam36-corr-1");
    assertThat(CorrelationId.currentOrNew()).isEqualTo("mam36-corr-1");
    interceptor.afterRecord(rec, consumer);
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-4] an event without a usable correlationId gets one generated id for all its log lines and events")
  @SuppressWarnings("unchecked")
  void missingCorrelationIdIsGenerated() {
    var interceptor = new CorrelationIdRecordInterceptor();
    Consumer<Object, Object> consumer = mock(Consumer.class);
    for (String value :
        new String[] {"{\"id\":\"e2\"}", "not json", "{\"correlationId\":\"a b\"}"}) {
      interceptor.intercept(record(value), consumer);
      assertThat(MDC.get(CorrelationId.MDC_KEY)).matches("[0-9a-f-]{36}");
      interceptor.afterRecord(record(value), consumer);
      assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
  }

  private final ApplicationContextRunner kafka =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.application.name=provider-service", "pem.health.kafka-timeout=1s")
          .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
          .withBean(
              KafkaAdmin.class, () -> new KafkaAdmin(Map.of("bootstrap.servers", "127.0.0.1:1")))
          .withConfiguration(AutoConfigurations.of(PemKafkaAutoConfiguration.class));

  @Test
  @DisplayName("[MAM-36 AC-4] a service with Kafka gets the correlation-id record interceptor")
  void interceptorIsConfigured() {
    kafka.run(
        ctx ->
            assertThat(ctx.getBean(RecordInterceptor.class))
                .isInstanceOf(CorrelationIdRecordInterceptor.class));
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-1] a service with Kafka reports Kafka in its health (component 'kafka'): DOWN when the broker cannot be reached")
  void kafkaHealthIsDownWithoutBroker() {
    kafka.run(
        ctx -> {
          HealthIndicator kafkaHealth = ctx.getBean("kafkaHealthIndicator", HealthIndicator.class);
          assertThat(kafkaHealth.health().getStatus()).isEqualTo(Status.DOWN);
        });
  }

  /** "log.level" read flat or nested, whichever form the ECS writer uses. */
  private static String field(JsonNode line, String dotted) {
    JsonNode flat = line.path(dotted);
    return flat.isMissingNode()
        ? line.at("/" + dotted.replace('.', '/')).asText(null)
        : flat.asText();
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-3] by default a log line is one JSON object with timestamp, level, service name, message and the correlationId")
  void logLinesAreJsonWithTheCorrelationId() throws Exception {
    MockEnvironment env = new MockEnvironment();
    new YamlPropertySourceLoader()
        .load("pem-common", new ClassPathResource("pem-common.yml"))
        .forEach(env.getPropertySources()::addLast);
    env.setProperty("spring.application.name", "provider-service");
    String format = env.getProperty("logging.structured.format.console");
    assertThat(format).isEqualTo("ecs");

    LoggerContext context = new LoggerContext();
    context.putObject(Environment.class.getName(), env);
    StructuredLogEncoder encoder = new StructuredLogEncoder();
    encoder.setContext(context);
    encoder.setFormat(format);
    encoder.start();
    LoggingEvent event =
        new LoggingEvent(
            "gov.medicaid.pem.Test",
            context.getLogger("gov.medicaid.pem.Test"),
            Level.INFO,
            "application validated",
            null,
            null);
    event.setMDCPropertyMap(Map.of(CorrelationId.MDC_KEY, "mam36-corr-3"));
    String line = new String(encoder.encode(event), StandardCharsets.UTF_8).trim();

    assertThat(line).doesNotContain("\n");
    JsonNode json = JSON.readTree(line);
    assertThat(field(json, "@timestamp")).isNotBlank();
    assertThat(field(json, "log.level")).isEqualTo("INFO");
    assertThat(field(json, "service.name")).isEqualTo("provider-service");
    assertThat(field(json, "message")).isEqualTo("application validated");
    assertThat(field(json, "correlationId")).isEqualTo("mam36-corr-3");
  }
}
