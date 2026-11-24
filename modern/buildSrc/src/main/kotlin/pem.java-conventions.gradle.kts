import pem.build.SkippedTestGuard

// Shared by every Java module: Java 21, Spring Boot BOM, Spotless, JUnit 5, JaCoCo with the 85% line gate (tdd-workflow 4).
plugins {
    java
    jacoco
    id("com.diffplug.spotless")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun v(name: String): String = catalog.findVersion(name).get().requiredVersion
fun lib(name: String) = catalog.findLibrary(name).get()

java {
    toolchain { languageVersion = JavaLanguageVersion.of(v("java")) }
}

dependencies {
    // jackson-bom: one version for every Jackson module, above the one in the Spring Boot BOM (MAM-84 AC-5, Q-122)
    val boms = listOf(
        platform(lib("spring-boot-bom")), platform(lib("spring-cloud-bom")), platform(lib("cucumber-bom")),
        platform(lib("jackson-bom")),
    )
    for (conf in listOf("implementation", "testImplementation", "annotationProcessor", "testAnnotationProcessor", "compileOnly")) {
        boms.forEach { add(conf, it) }
    }

    // MAM-84 AC-5, Q-122: security fixes above what the Spring Boot BOM or a starter brings (versions in gradle/libs.versions.toml).
    // A constraint only raises a library that is already on the class path; the higher version wins, so a later BOM bump is kept.
    constraints {
        listOf(
            "tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket", "postgresql-driver", "snappy-java", "lz4-java",
            "bouncycastle-bcprov", "log4j-api", "log4j-to-slf4j", "httpclient5", "httpcore5", "httpcore5-h2",
        ).forEach { alias ->
            val dependency = lib(alias).get()
            add("implementation", "${dependency.module}:${dependency.versionConstraint.requiredVersion}") {
                because("MAM-84 AC-5, Q-122: security fix, see docs/reports/dependency-scan.md and ADR-010")
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
}

spotless {
    // Keep each file's own line endings (2026-11-20, first CI run): the sources are CRLF in git (written on Windows, no
    // .gitattributes), and the default policy expects the platform's endings, so every file failed spotlessCheck on Linux.
    lineEndings = com.diffplug.spotless.LineEnding.PRESERVE
    java {
        target("src/**/*.java")
        googleJavaFormat() // the version Spotless 7.2.1 ships with
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

jacoco { toolVersion = v("jacoco") }

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "448m"
    // 128 MB of code cache: with TieredStopAtLevel=1 the JVM reserves only 48 MB, and the one test JVM of a module with
    // about 700 tests filled it (screening-service, 2026-11-21: 8 tests failed with "Out of space in CodeCache").
    jvmArgs("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:ReservedCodeCacheSize=128m", "-Xshare:auto")
    systemProperty("cucumber.publish.quiet", "true")
    // Report each Scenario Outline example under the outline's own name, so the rule trace can link it to its @AC tags.
    systemProperty("cucumber.junit-platform.naming-strategy.short.example-name", "pickle")
    systemProperty("user.timezone", "UTC")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)

    // MAM-16 AC-7: no skipped test may reach green. A @Disabled test, a failed assumption or an unmet condition fails this
    // task after the run and names each skipped test (JUnit and Cucumber alike); a run without skipped tests is unchanged.
    val skippedGuard = SkippedTestGuard(path, logger)
    addTestListener(skippedGuard)
    doLast { skippedGuard.failIfAny() }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Line coverage gate: at least 85% per module (main application classes and generated code excluded).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude("**/*Application.class", "**/generated/**") }
    }))
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(":versionCheck") // MAM-16 AC-8: every version of the catalog and of the build files is exact (root project task)
    dependsOn(":testSourceCheck") // MAM-16 AC-7: no skip mechanism in a test source (root project task)
}
