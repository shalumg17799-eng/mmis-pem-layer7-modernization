package gov.medicaid.pem.enrollment.application;

import java.util.Optional;
import java.util.UUID;

/** Read port: one application by its public id. */
public interface ApplicationReader {

  Optional<OwnedApplication> byId(UUID applicationId);
}
