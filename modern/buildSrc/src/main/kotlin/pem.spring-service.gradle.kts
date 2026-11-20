// A Spring Boot microservice (services/*): web, security, JPA, Flyway, Kafka, actuator, OpenAPI; Cucumber, Testcontainers,
// ArchUnit; PIT with the 70% mutation gate on the domain and application packages (tdd-workflow 4, java-microservices 12).
import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("pem.java-conventions")
    id("org.springframework.boot")
    id("info.solidsoft.pitest")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun v(name: String): String = catalog.findVersion(name).get().requiredVersion
fun lib(name: String) = catalog.findLibrary(name).get()

// gov.medicaid.pem.<svc>: the service name without "-service" (java-microservices 5)
val svc = project.name.removeSuffix("-service").replace("-", "")
val basePackage = "gov.medicaid.pem.$svc"
extra["basePackage"] = basePackage

dependencies {
    implementation(project(":libs:service-kit"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(lib("springdoc-webmvc"))
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":libs:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation(lib("archunit-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}

// Mutation testing: unit tests of the domain and application packages only (integration tests are not run per mutant).
configure<PitestPluginExtension> {
    pitestVersion = v("pitest")
    junit5PluginVersion = v("pitest-junit5")
    targetClasses = setOf("$basePackage.domain.*", "$basePackage.application.*")
    targetTests = setOf("$basePackage.domain.*", "$basePackage.application.*")
    threads = 1
    useClasspathFile = true  // Windows: the classpath is too long for a command line
    jvmArgs = listOf("-Xmx256m", "-XX:+UseSerialGC")
    mutationThreshold = 70
    failWhenNoMutations = false
    timestampedReports = false
    outputFormats = setOf("HTML", "XML")
}

tasks.named("check") { dependsOn("pitest") }
