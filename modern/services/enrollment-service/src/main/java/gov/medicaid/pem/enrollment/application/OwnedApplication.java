package gov.medicaid.pem.enrollment.application;

/** An application with the login name of the user who submitted it (who may read it, D-023). */
public record OwnedApplication(StoredApplication application, String submittedBy) {}
