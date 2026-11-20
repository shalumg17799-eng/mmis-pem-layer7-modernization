package gov.medicaid.pem.kit.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.web.CorrelationId;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * MAM-36 AC-4: while a consumed event is handled, the correlationId of its envelope is in the MDC,
 * so the consumer's log lines and the events it publishes (OutboxWriter) carry the same id. An
 * event without a usable id gets one generated id for everything it causes.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public ConsumerRecord<Object, Object> intercept(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    MDC.put(CorrelationId.MDC_KEY, CorrelationId.sanitize(correlationId(record.value())));
    return record;
  }

  @Override
  public void afterRecord(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    MDC.remove(CorrelationId.MDC_KEY);
  }

  static String correlationId(Object value) {
    if (!(value instanceof String text)) {
      return null;
    }
    try {
      JsonNode id = JSON.readTree(text).path("correlationId");
      return id.isTextual() ? id.asText() : null;
    } catch (Exception notAnEnvelope) {
      return null;
    }
  }
}
