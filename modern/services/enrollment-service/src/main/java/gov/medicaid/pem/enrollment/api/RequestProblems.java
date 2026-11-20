package gov.medicaid.pem.enrollment.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 400 problem+json for a body that is not JSON and for invalid query parameters (contract: 400
 * Invalid request).
 */
@RestControllerAdvice
public class RequestProblems {

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    HandlerMethodValidationException.class
  })
  public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is not valid.");
    p.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(p);
  }
}
