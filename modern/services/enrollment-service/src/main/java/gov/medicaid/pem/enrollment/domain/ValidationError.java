package gov.medicaid.pem.enrollment.domain;

/**
 * One failed check: the legacy code and the request field it is about (D-001: one error per failed
 * check).
 */
public record ValidationError(ErrorCode code, String field) {}
