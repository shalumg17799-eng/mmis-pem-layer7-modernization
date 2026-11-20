package gov.medicaid.pem.screening;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.PemContainers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Events as the other services publish them to screening-service: ProviderRegistered on
 * pem.provider.events and RevalidationDue on pem.revalidation.events, with the NPI as key
 * (modern/contracts/asyncapi/events.yaml).
 */
public final class ScreeningEvents {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ScreeningEvents() {}

  /** The envelope text of an event. */
  public static String envelope(
      UUID id,
      String type,
      String source,
      String npi,
      String correlationId,
      Map<String, Object> data) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", source);
    e.put("time", "2026-11-20T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", correlationId);
    e.put("data", data);
    try {
      return JSON.writeValueAsString(e);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Sends any text on a topic with the key. */
  public static void send(String topic, String key, String text) {
    EventProbe.send(PemContainers.KAFKA.getBootstrapServers(), topic, key, text);
  }

  /** ProviderRegistered for the NPI; returns the event id. */
  public static UUID providerRegistered(String npi) {
    return providerRegistered(UUID.randomUUID(), npi, UUID.randomUUID().toString());
  }

  public static UUID providerRegistered(UUID id, String npi, String correlationId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", npi);
    data.put("medicaidId", "MCD000" + npi.substring(7));
    data.put("providerType", "IN");
    data.put("taxonomy", "207Q00000X");
    data.put("enrollStatus", "P");
    data.put("screenFlag", "U");
    data.put("displayName", "TEST PROVIDER");
    send(
        "pem.provider.events",
        npi,
        envelope(id, "ProviderRegistered", "provider-service", npi, correlationId, data));
    return id;
  }

  /** RevalidationDue for the NPI; returns the event id. */
  public static UUID revalidationDue(String npi, String dueDate) {
    return revalidationDue(UUID.randomUUID(), npi, dueDate, UUID.randomUUID().toString());
  }

  public static UUID revalidationDue(UUID id, String npi, String dueDate, String correlationId) {
    send(
        "pem.revalidation.events",
        npi,
        envelope(
            id,
            "RevalidationDue",
            "revalidation-service",
            npi,
            correlationId,
            revalidationData(npi, dueDate)));
    return id;
  }

  public static Map<String, Object> revalidationData(String npi, String dueDate) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("caseId", UUID.randomUUID().toString());
    data.put("npi", npi);
    data.put("dueDate", dueDate);
    return data;
  }
}
