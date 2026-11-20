package gov.medicaid.pem.kit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

class ProblemHandlerTest {

  private final ProblemHandler handler = new ProblemHandler();

  @Test
  @DisplayName(
      "[MAM-29 AC-6] an API error becomes RFC 7807 problem details with the legacy code and field errors")
  void apiExceptionToProblem() {
    var ex =
        new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "E104",
            "Taxonomy is required",
            List.of(new ApiFieldError("taxonomy", "E104", "Taxonomy is required")));

    ProblemDetail p = handler.handleApi(ex).getBody();

    assertThat(p.getStatus()).isEqualTo(422);
    assertThat(p.getTitle()).isEqualTo("Unprocessable Entity");
    assertThat(p.getDetail()).isEqualTo("Taxonomy is required");
    assertThat(p.getProperties()).containsEntry("code", "E104");
    @SuppressWarnings("unchecked")
    var errors = (List<Map<String, String>>) p.getProperties().get("errors");
    assertThat(errors)
        .containsExactly(
            Map.of("field", "taxonomy", "code", "E104", "message", "Taxonomy is required"));
  }

  @Test
  @DisplayName("[MAM-29 AC-6] a not-found error has status 404 and no legacy code")
  void notFound() {
    ProblemDetail p =
        handler.handleApi(ApiException.notFound("Provider 1003000999 not found")).getBody();
    assertThat(p.getStatus()).isEqualTo(404);
    assertThat(p.getProperties()).doesNotContainKey("code");
  }

  @Test
  @DisplayName("[MAM-34 AC-2] access denied becomes 403 problem details")
  void accessDenied() {
    ProblemDetail p = handler.handleDenied(new AccessDeniedException("no")).getBody();
    assertThat(p.getStatus()).isEqualTo(403);
    assertThat(p.getTitle()).isEqualTo("Forbidden");
  }
}
