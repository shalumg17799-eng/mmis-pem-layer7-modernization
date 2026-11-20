package gov.medicaid.pem.revalidation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One revalidation case of a provider (D-029: at most one open case per provider). */
public record RevalidationCase(
    UUID caseId,
    String npi,
    LocalDate dueDate,
    CaseStatus status,
    Instant openedAt,
    Instant closedAt) {}
