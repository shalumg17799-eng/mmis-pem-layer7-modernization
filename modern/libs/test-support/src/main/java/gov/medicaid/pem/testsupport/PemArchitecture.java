package gov.medicaid.pem.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The layering every service follows (java-microservices 5): {@code domain} depends on nothing
 * outside the domain and the JDK (no Spring, no persistence); {@code api} never touches {@code
 * infrastructure}; {@code domain} and {@code application} never depend on {@code api}.
 */
public final class PemArchitecture {

  private PemArchitecture() {}

  public static void check(String basePackage) {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(basePackage);
    noClasses()
        .that()
        .resideInAPackage(basePackage + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "org.hibernate..",
            basePackage + ".api..",
            basePackage + ".application..",
            basePackage + ".infrastructure..",
            basePackage + ".adapter..")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage(basePackage + ".api..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(basePackage + ".infrastructure..")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that()
        .resideInAnyPackage(basePackage + ".application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(basePackage + ".api..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
