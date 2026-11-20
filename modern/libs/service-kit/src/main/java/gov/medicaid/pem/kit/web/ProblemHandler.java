package gov.medicaid.pem.kit.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps errors to RFC 7807 problem details: {@code code} carries the legacy error code, {@code
 * errors} the fields.
 *
 * <p>This is the default for every service. A service's own advice for the same exception wins: it
 * is registered by component scanning before this auto-configured bean, and advices of equal order
 * are consulted in registration order.
 */
@RestControllerAdvice
public class ProblemHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    p.setTitle(ex.status().getReasonPhrase());
    p.setProperties(new java.util.LinkedHashMap<>());
    if (ex.code() != null) {
      p.setProperty("code", ex.code());
    }
    if (!ex.errors().isEmpty()) {
      p.setProperty("errors", fieldErrors(ex.errors()));
    }
    return ResponseEntity.status(ex.status()).body(p);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You are not allowed to do this.");
    p.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(p);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleInvalid(MethodArgumentNotValidException ex) {
    List<ApiFieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiFieldError(f.getField(), null, f.getDefaultMessage()))
            .toList();
    return handleApi(
        new ApiException(HttpStatus.BAD_REQUEST, null, "The request is not valid.", errors));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return handleApi(
        ApiException.badRequest("Parameter '" + ex.getName() + "' has an invalid value."));
  }

  /**
   * A body that is not JSON, has the wrong type or shape, or is missing: a 400 problem (AC-4 of
   * MAM-38 and MAM-41), not the container's empty 400. The detail says nothing about the parser.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadable() {
    return handleApi(
        ApiException.badRequest(
            "The request body is not valid: send a JSON document of the expected shape."));
  }

  private static List<Map<String, String>> fieldErrors(List<ApiFieldError> errors) {
    return errors.stream()
        .map(
            e -> {
              var m = new java.util.LinkedHashMap<String, String>();
              m.put("field", e.field());
              if (e.code() != null) {
                m.put("code", e.code());
              }
              if (e.message() != null) {
                m.put("message", e.message());
              }
              return (Map<String, String>) m;
            })
        .toList();
  }
}
