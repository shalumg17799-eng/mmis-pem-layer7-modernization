plugins { id("pem.spring-service") }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation(project(":libs:fixed-width"))
    testImplementation(libs.wiremock.standalone)
    // MAM-65 AC-7: the HAPI FHIR validator checks the live Bundles against FHIR R4 (tests only, ADR-010)
    testImplementation(libs.hapi.fhir.structures.r4)
    testImplementation(libs.hapi.fhir.validation)
    testImplementation(libs.hapi.fhir.validation.resources.r4)
    testImplementation(libs.hapi.fhir.caching.caffeine)
}
