package gov.medicaid.pem.tmsis.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * MAM-75 AC-8: a start request whose body cannot be read (for example a businessDate that is not a
 * date) is a 400 problem+json, like a start without a businessDate.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class TmsisProblems {

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The request body is not valid: businessDate must be a date.");
    p.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    return ResponseEntity.badRequest().body(p);
  }
}
