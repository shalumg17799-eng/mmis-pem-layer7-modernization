package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.kit.web.CorrelationId;
import gov.medicaid.pem.provider.domain.Provider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Write port of the provider history (MAM-40, audit_log): append only, never updated or deleted
 * (Q-012). Every change of a provider's record is appended in the transaction of the change, with
 * the correlation id of the request or event being handled.
 */
public interface AuditTrail {

  /** No history (unit tests that do not wire one). */
  AuditTrail NONE = entry -> {};

  void append(AuditEntry entry);

  /** Appends one entry for {@code npi}; the correlation id is taken from the log context. */
  default void record(
      String npi,
      String actor,
      String action,
      Map<String, Object> before,
      Map<String, Object> after) {
    append(new AuditEntry(npi, null, actor, action, before, after, MDC.get(CorrelationId.MDC_KEY)));
  }

  /** The provider fields that a change can touch, as shown in before and after. */
  static Map<String, Object> snapshot(Provider p) {
    if (p == null) {
      return null;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("npi", p.npi());
    m.put("medicaidId", p.medicaidId());
    m.put("providerType", p.providerType() == null ? null : p.providerType().code());
    m.put("taxonomy", p.taxonomy());
    m.put("riskTier", p.riskTier() == null ? null : p.riskTier().code());
    m.put("enrollStatus", p.enrollStatus() == null ? null : p.enrollStatus().code());
    m.put("screenFlag", p.screenFlag() == null ? null : p.screenFlag().code());
    m.put("effectiveDate", p.effectiveDate());
    m.put("endDate", p.endDate());
    m.put("revalidationDueDate", p.revalidationDueDate());
    m.put("version", p.version());
    return m;
  }

  /** {@code snapshot} with one extra named part (the request details of the change). */
  static Map<String, Object> snapshot(Provider p, String part, Map<String, Object> details) {
    Map<String, Object> m = snapshot(p);
    m.put(part, details);
    return m;
  }

  /** A map that keeps its order and allows null values. */
  static Map<String, Object> details(Object... keysAndValues) {
    Map<String, Object> m = new LinkedHashMap<>();
    List<Object> kv = java.util.Arrays.asList(keysAndValues);
    for (int i = 0; i + 1 < kv.size(); i += 2) {
      m.put((String) kv.get(i), kv.get(i + 1));
    }
    return m;
  }
}
