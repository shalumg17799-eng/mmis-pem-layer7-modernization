plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle)
    implementation(libs.pitest.gradle)
    implementation(libs.spotless.gradle)
}
