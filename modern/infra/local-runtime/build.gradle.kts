plugins {
    id("pem.java-conventions")
    id("org.springframework.boot")
}

// ADR-011: runs every service in one JVM for small machines; each service keeps its own Spring context, port,
// database and Kafka consumer group. Production-style deployment uses one container per service instead.
dependencies {
    implementation("org.springframework.boot:spring-boot")
    implementation(project(":services:api-gateway"))
    implementation(project(":services:provider-service"))
    implementation(project(":services:enrollment-service"))
    implementation(project(":services:screening-service"))
    implementation(project(":services:risk-service"))
    implementation(project(":services:revalidation-service"))
    implementation(project(":services:sanctions-service"))
    implementation(project(":services:notification-service"))
    implementation(project(":services:directory-service"))
    implementation(project(":services:tmsis-service"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { archiveFileName = "pem-local-runtime.jar" }

springBoot { mainClass = "gov.medicaid.pem.localruntime.LocalRuntime" }
