package gov.medicaid.pem.notification.application;

/** A notice template (notice_template): code, subject and a body where {npi} is replaced. */
public record Template(String code, String subject, String body) {

  public String bodyFor(String npi) {
    return body.replace("{npi}", npi);
  }
}
