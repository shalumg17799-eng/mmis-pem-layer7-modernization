package gov.medicaid.pem.tmsis.application;

import java.time.LocalDate;
import java.util.UUID;

/** Data of the ExtractProduced event (contracts/asyncapi/events.yaml#ExtractProducedData). */
public record ExtractProducedData(
    UUID runId, LocalDate businessDate, String periodYyddd, int rowCount) {}
