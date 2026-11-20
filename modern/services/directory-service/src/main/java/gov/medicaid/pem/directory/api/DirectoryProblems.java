package gov.medicaid.pem.directory.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * MAM-67 AC-2: a start request whose body cannot be read (a businessDate that is not an ISO date)
 * is a 400 problem+json and creates no run.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class DirectoryProblems {

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The request body is not valid: businessDate must be an ISO date (yyyy-mm-dd).");
    p.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    return ResponseEntity.badRequest().body(p);
  }
}
