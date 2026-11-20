package gov.medicaid.pem.enrollment.application;

/**
 * Filters of listApplications. When {@code submittedBy} is set the caller is a PROVIDER, who sees
 * only the applications they submitted or that carry their own NPI ({@code ownNpi}, from the token
 * claim).
 */
public record ApplicationQuery(
    String status, String npi, String submittedBy, String ownNpi, int page, int size) {}
