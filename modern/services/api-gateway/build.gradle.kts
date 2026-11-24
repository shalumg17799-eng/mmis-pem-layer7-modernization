plugins {
    id("pem.java-conventions")
    id("org.springframework.boot")
}

// Spring Cloud Gateway (server, Web MVC flavour): routes /api/v1/** and /fhir/** to the services, CORS for the web UI,
// passes the bearer token through, adds X-Correlation-Id.
dependencies {
    implementation(project(":libs:service-kit"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // ADR-013: the HTTP client behind the proxy routes (a read limit per read, no repeated write, PATCH). No version here: the
    // Spring Boot BOM manages 5.5.2, and the constraints of pem.java-conventions raise it (and httpcore5, httpcore5-h2) to the
    // versions of gradle/libs.versions.toml (ADR-010, Q-122; security fixes, none in the 5.5.x line).
    implementation("org.apache.httpcomponents.client5:httpclient5")
    testImplementation(project(":libs:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { archiveFileName = "api-gateway.jar" }
