plugins { id("pem.spring-service") }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation(project(":libs:fixed-width"))
    testImplementation(libs.wiremock.standalone)
}
