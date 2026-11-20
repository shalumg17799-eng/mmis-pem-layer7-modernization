package gov.medicaid.pem.enrollment.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PRVENR01 VALIDATE-APPLICATION (lines 127-148) and the duplicate branch of WRITE-PROVIDER-RECORD
 * (160-166), rebuilt so that every check runs and each failure is reported once (D-001, D-016).
 * Unlike the legacy GO TO, a failed check never stops the others.
 */
public final class ApplicationValidator {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");

  private ApplicationValidator() {}

  public static boolean isNpi(String npi) {
    return npi != null && NPI.matcher(npi).matches();
  }

  public static Validation validate(
      String npi,
      String providerType,
      String taxonomy,
      ProviderTypeMapping mapping,
      boolean npiAlreadyValidated) {
    List<ValidationError> errors = new ArrayList<>();
    if (!isNpi(npi)) {
      errors.add(new ValidationError(ErrorCode.E101, "npi"));
    } else if (npiAlreadyValidated) {
      errors.add(new ValidationError(ErrorCode.E103, "npi"));
    }
    String legacyType = mapping.toLegacy(providerType).orElse(null);
    if (legacyType == null) {
      errors.add(new ValidationError(ErrorCode.E102, "providerType"));
    }
    if (taxonomy == null || taxonomy.isBlank()) {
      errors.add(new ValidationError(ErrorCode.E104, "taxonomy"));
    }
    return new Validation(errors, legacyType);
  }
}
