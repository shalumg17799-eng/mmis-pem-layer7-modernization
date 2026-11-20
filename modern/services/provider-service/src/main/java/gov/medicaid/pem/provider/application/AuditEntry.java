package gov.medicaid.pem.provider.application;

import java.time.Instant;
import java.util.Map;

/**
 * One row of a provider's history (audit_log, MAM-40): who, when, what, the state before and after,
 * and the request or event it belongs to. {@code at} is null until the entry is stored.
 */
public record AuditEntry(
    String npi,
    Instant at,
    String actor,
    String action,
    Map<String, Object> before,
    Map<String, Object> after,
    String correlationId) {}
