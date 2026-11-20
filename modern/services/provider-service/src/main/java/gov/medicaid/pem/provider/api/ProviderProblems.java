package gov.medicaid.pem.provider.api;

import gov.medicaid.pem.kit.web.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * provider-service problems beyond the kit's ProblemHandler.
 *
 * <ul>
 *   <li>MAM-37 AC-8 (R-PRVCICS1-ADD-04, D-024): a database that cannot be read is a 503 problem,
 *       never a 404, logged with the request's correlation id (the kit's filter sets the
 *       X-Correlation-Id header on every answer).
 *   <li>MAM-40 AC-8: a method that is not defined on a path (PUT, PATCH or DELETE on the
 *       append-only history) is a 405 problem.
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProviderProblems {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderProblems.class);

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  public ResponseEntity<ProblemDetail> readFailure(RuntimeException ex) {
    LOG.error(
        "provider records could not be read or written (correlation id {}): {}",
        MDC.get(CorrelationId.MDC_KEY),
        ex.toString());
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The provider records cannot be reached right now. Please try again later.");
    p.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(p);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not allowed here.");
    p.setTitle(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase());
    var answer = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
    if (ex.getSupportedHttpMethods() != null) {
      answer.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
    }
    return answer.body(p);
  }
}
