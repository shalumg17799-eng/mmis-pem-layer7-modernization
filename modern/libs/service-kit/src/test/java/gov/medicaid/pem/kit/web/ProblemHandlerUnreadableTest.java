package gov.medicaid.pem.kit.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Written after the code (handleUnreadable exists already): the unit-level shape of the 400 that
 * the kit answers for a request body that cannot be read. The wiring (a real request, a service's
 * own advice) is in {@code UnreadableBodyWebTest}.
 */
class ProblemHandlerUnreadableTest {

  /** Words that would show the parser (Jackson, Spring) to the caller. */
  static final String[] PARSER_WORDS = {
    "Jackson",
    "JSON parse error",
    "Unrecognized token",
    "Unexpected character",
    "Cannot deserialize",
    "MismatchedInput",
    "InvalidFormat",
    "JsonParse",
    "com.fasterxml",
    "java.lang",
    "line:",
    "column:",
    "HttpMessageNotReadable",
  };

  private final ResponseEntity<ProblemDetail> answer = new ProblemHandler().handleUnreadable();

  @Test
  @DisplayName("[MAM-29 AC-6] an unreadable body is answered with status 400")
  void statusIs400() {
    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(answer.getBody().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("[MAM-29 AC-6] the problem for an unreadable body has the title Bad Request")
  void titleIsBadRequest() {
    assertThat(answer.getBody().getTitle()).isEqualTo("Bad Request");
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the detail of an unreadable body is a short sentence that says nothing about the parser")
  void detailSaysNothingAboutTheParser() {
    String detail = answer.getBody().getDetail();

    assertThat(detail).isNotBlank().hasSizeLessThanOrEqualTo(120);
    assertThat(detail).doesNotContain(PARSER_WORDS);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the problem for an unreadable body has no legacy code and no field errors (nothing is invented)")
  void noCodeAndNoFieldErrors() {
    assertThat(answer.getBody().getProperties()).isNullOrEmpty();
  }
}
