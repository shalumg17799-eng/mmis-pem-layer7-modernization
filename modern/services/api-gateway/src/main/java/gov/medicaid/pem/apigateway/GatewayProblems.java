package gov.medicaid.pem.apigateway;

import gov.medicaid.pem.kit.web.CorrelationId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Gateway errors (unknown path, bad request) as RFC 7807 problem details carrying the correlation
 * id.
 */
@RestControllerAdvice
public class GatewayProblems extends ResponseEntityExceptionHandler {

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ProblemDetail p) {
      p.setProperty("correlationId", CorrelationId.currentOrNew());
    }
    return super.createResponseEntity(body, headers, status, request);
  }
}
