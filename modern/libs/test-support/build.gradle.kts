plugins { id("pem.java-library") }

// Shared test fixtures only (no production code): Testcontainers, JWT helpers, ArchUnit rules, event capture.
val catalog = the<VersionCatalogsExtension>().named("libs")
dependencies {
    api("org.springframework.boot:spring-boot-starter-test")
    api("org.springframework.security:spring-security-test")
    api("org.springframework.boot:spring-boot-testcontainers")
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
    api("org.testcontainers:kafka")
    api("org.springframework.kafka:spring-kafka")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.yaml:snakeyaml")
    api("org.springframework.security:spring-security-oauth2-jose")
    api("org.springframework.security:spring-security-oauth2-resource-server")
    api(catalog.findLibrary("archunit-junit5").get())
}
