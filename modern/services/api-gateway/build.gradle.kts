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
    testImplementation(project(":libs:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { archiveFileName = "api-gateway.jar" }
