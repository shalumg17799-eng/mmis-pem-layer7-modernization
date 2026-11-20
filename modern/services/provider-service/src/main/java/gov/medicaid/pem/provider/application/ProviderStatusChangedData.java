package gov.medicaid.pem.provider.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/**
 * Data of the ProviderStatusChanged event
 * (contracts/asyncapi/events.yaml#ProviderStatusChangedData). A member without a value is left out
 * (the contract says string, not null); only the due date, which the contract allows to be null, is
 * always written.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderStatusChangedData(
    String npi,
    String from,
    String to,
    String reason,
    String actor,
    @JsonInclude(JsonInclude.Include.ALWAYS) LocalDate revalidationDueDate,
    String providerType,
    String displayName,
    String taxonomy,
    String medicaidId) {}
