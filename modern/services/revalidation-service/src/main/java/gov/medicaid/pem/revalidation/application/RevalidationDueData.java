package gov.medicaid.pem.revalidation.application;

import java.time.LocalDate;
import java.util.UUID;

/** Data of the RevalidationDue event (contracts/asyncapi/events.yaml#RevalidationDueData). */
public record RevalidationDueData(UUID caseId, String npi, LocalDate dueDate) {}
