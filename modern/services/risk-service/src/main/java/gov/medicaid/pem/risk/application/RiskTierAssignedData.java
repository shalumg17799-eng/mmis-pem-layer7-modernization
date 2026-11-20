package gov.medicaid.pem.risk.application;

/** Data of the RiskTierAssigned event (contracts/asyncapi/events.yaml#RiskTierAssignedData). */
public record RiskTierAssignedData(String npi, String taxonomy, String tier, int ruleSetVersion) {}
