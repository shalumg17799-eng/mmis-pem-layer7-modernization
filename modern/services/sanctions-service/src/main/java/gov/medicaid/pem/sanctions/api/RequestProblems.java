package gov.medicaid.pem.sanctions.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * 400 problem+json for an upload whose multipart message cannot be read (MAM-55 AC-1; lesson h of
 * the code reviews: an unreadable body is a 400 problem, never a 500): no boundary parameter in the
 * content type, a part cut off before its closing boundary or inside its headers. The web server
 * raises every such parse failure as a {@link MultipartException} before the controller runs, so no
 * run is started. A file over the size limit of the web server stays 413, now as problem+json
 * (Q-120: the limit itself is not declared in the contract); it is a subclass of {@code
 * MultipartException} and needs the more specific handler.
 */
@RestControllerAdvice
public class RequestProblems {

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ProblemDetail> unreadableUpload() {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The upload could not be read: send the file and the businessDate as a complete"
                + " multipart/form-data message.");
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
