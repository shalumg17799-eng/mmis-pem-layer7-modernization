package gov.medicaid.pem.enrollment.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Data of the ApplicationValidated event (contracts/asyncapi/events.yaml#ApplicationValidatedData).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationValidatedData(
    UUID applicationId,
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    NewApplication.Individual individual,
    NewApplication.Organization organization) {}
