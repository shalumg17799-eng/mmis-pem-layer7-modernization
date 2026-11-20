// Root project: the conventions live in buildSrc (pem.java-library, pem.spring-service).
import pem.build.VersionCheck

// MAM-16 AC-8: every module's check runs this (see pem.java-conventions). It reads gradle/libs.versions.toml, or the file given
// with -PpemVersionCatalog=<path> (used only by tools/checks/demo-build-gates.sh), and the build files for inline versions.
val versionCheck = tasks.register<VersionCheck>("versionCheck") {
    group = "verification"
    description = "Fails on a dynamic (+, latest, range), empty or missing version in the version catalog or in a build file."
    catalog.fileProvider(providers.gradleProperty("pemVersionCatalog").map { file(it) }.orElse(file("gradle/libs.versions.toml")))
    buildFiles.from(
        file("build.gradle.kts"),
        file("settings.gradle.kts"),
        fileTree("buildSrc") {
            include("**/*.kts")
            exclude("**/build/**", "**/.gradle/**")
        },
        subprojects.map { it.buildFile },
    )
    baseDirectory = layout.projectDirectory
}

tasks.register("checkAll") {
    group = "verification"
    description = "Runs versionCheck and check (tests, JaCoCo, PIT, Spotless) on every library and service."
    dependsOn(versionCheck)
    // Only projects that have a check task: the folders :libs, :services and :infra are plain containers without a build file,
    // so a path such as ":libs:check" does not exist. The collections are live, so they are filled when the graph is built.
    dependsOn(subprojects.map { p -> p.tasks.matching { it.name == "check" } })
}
