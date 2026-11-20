package gov.medicaid.pem.enrollment.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * 400 problem+json for a body that is not JSON, for a multipart message that cannot be read and for
 * invalid query parameters (contract: 400 Invalid request); a file over the size limit of the web
 * server stays 413, now as problem+json (Q-120: the limit itself is not declared in the contract).
 */
@RestControllerAdvice
public class RequestProblems {

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    MultipartException.class
  })
  public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is not valid.");
    p.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(p);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ProblemDetail> tooLarge() {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large.");
    p.setTitle(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(p);
  }
}
