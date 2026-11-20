// A plain Java library (libs/*).
plugins {
    `java-library`
    id("pem.java-conventions")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
