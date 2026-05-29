import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("campuscoffee.java-conventions")
    id("campuscoffee.jacoco-conventions")
    id("campuscoffee.pitest-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api"))
    runtimeOnly(project(":data"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.cloud.starter.openfeign)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.rest.assured) {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.archunit)
    testImplementation(libs.wiremock.standalone)
}

// Match the Maven artifact name the Dockerfile/compose expect (application-0.0.5.jar).
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("application-${project.version}.jar")
}

// Only the executable bootJar is consumed; drop the redundant plain library jar.
tasks.named("jar") {
    enabled = false
}

// Cross-module mutation: mutate the api and data classes against this module's system and
// acceptance tests (the only tests that exercise the controllers), mirroring the Maven build's
// crossModule run. Opt-in and local: `gradle :application:pitest -Pmutation`.
configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.api.*", "de.seuhd.campuscoffee.data.*"))
    additionalMutableCodePaths.set(
        listOf(
            project(":api").layout.buildDirectory.dir("classes/java/main").get().asFile,
            project(":data").layout.buildDirectory.dir("classes/java/main").get().asFile,
        )
    )
}
tasks.named("pitest") {
    dependsOn(":api:classes", ":data:classes")
}
