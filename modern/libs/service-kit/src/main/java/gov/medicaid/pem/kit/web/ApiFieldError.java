package gov.medicaid.pem.kit.web;

/**
 * One field error of a problem response: the field, its legacy error code (E101...) and a readable
 * message.
 */
public record ApiFieldError(String field, String code, String message) {}
