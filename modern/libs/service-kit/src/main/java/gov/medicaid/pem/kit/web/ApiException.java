package gov.medicaid.pem.kit.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * An error returned as RFC 7807 problem details, with a legacy error code where one applies
 * (D-016).
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<ApiFieldError> errors;

  public ApiException(HttpStatus status, String code, String detail, List<ApiFieldError> errors) {
    super(detail);
    this.status = status;
    this.code = code;
    this.errors = List.copyOf(errors);
  }

  public static ApiException notFound(String detail) {
    return new ApiException(HttpStatus.NOT_FOUND, null, detail, List.of());
  }

  public static ApiException conflict(String code, String detail) {
    return new ApiException(HttpStatus.CONFLICT, code, detail, List.of());
  }

  public static ApiException badRequest(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, null, detail, List.of());
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<ApiFieldError> errors() {
    return errors;
  }
}
