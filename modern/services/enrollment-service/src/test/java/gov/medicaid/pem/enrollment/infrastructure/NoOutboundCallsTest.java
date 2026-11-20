package gov.medicaid.pem.enrollment.infrastructure;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lesson d of the code reviews (a call to another service that does not answer must end in a
 * visible failure within the configured timeout, never a hang) does not apply to this service
 * today: it calls no other service. The intake reads an uploaded file and writes its own database
 * and outbox. This test keeps that statement true: the day the service gets an HTTP client (JDK
 * client, Spring RestClient, RestTemplate, WebClient or the kit's service clients), this test fails
 * and the new call needs its own test of the timeout (a FAILED run or a 503, never a hang) before
 * this rule is changed.
 */
class NoOutboundCallsTest {

  @Test
  @DisplayName(
      "[MAM-56 AC-1] [lesson d] the enrollment service uses no HTTP client: it calls no other service, so no timeout can hang a run")
  void noHttpClientIsUsed() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("gov.medicaid.pem.enrollment");
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "java.net.http..",
            "org.springframework.web.client..",
            "org.springframework.web.reactive.function.client..",
            "org.apache.hc..",
            "okhttp3..",
            "gov.medicaid.pem.kit.client..")
        .check(classes);
  }
}
