plugins { id("pem.java-library") }

// Cross-cutting production code shared by every service: event envelope, transactional outbox, idempotent consumer,
// correlation IDs, RFC 7807 errors, Keycloak role mapping, service-to-service tokens. Auto-configured; each part switches
// on only when its classes and beans are present (the gateway has no database, Kafka or resource server).
val springOptional = listOf(
    "org.springframework.boot:spring-boot-starter-jdbc",
    "org.springframework.boot:spring-boot-starter-security",
    "org.springframework.boot:spring-boot-starter-oauth2-resource-server",
    "org.springframework.kafka:spring-kafka",
    "org.springframework.boot:spring-boot-actuator",
)
dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    springOptional.forEach { compileOnly(it); testImplementation(it) }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
