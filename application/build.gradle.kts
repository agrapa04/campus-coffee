import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("de.seuhd.campuscoffee.java-conventions")
    id("de.seuhd.campuscoffee.kotlin-conventions")
    id("de.seuhd.campuscoffee.kotlin-kapt-conventions")
    id("de.seuhd.campuscoffee.jacoco-conventions")
    id("de.seuhd.campuscoffee.pitest-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api"))
    runtimeOnly(project(":data"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    // Spring Security (HTTP Basic + the filter chain) and OAuth2 resource server (JWT bearer tokens).
    // The starter ships a working-but-permissive setup; the assignment tightens it.
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    // generate spring-configuration-metadata.json for JwtProperties so the IDE resolves the jwt.* keys
    kapt(libs.spring.boot.configuration.processor)

    // The JDBC driver reaches the runtime classpath transitively via data, but declaring it on the
    // deployable module makes the runtime dependency explicit and lets the IDE resolve the
    // driver-class-name in application.yaml.
    runtimeOnly(libs.postgresql)

    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.archunit)
    testImplementation(libs.wiremock.standalone)
}

// Name the boot jar application.jar (version-independent) so the Dockerfile references a stable
// name instead of a version-coupled application-<version>.jar.
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("application.jar")
}

springBoot {
    // Write META-INF/build-info.properties so a BuildProperties bean exposes the version at runtime
    // (consumed by OpenApiConfig and OsmClientConfig), keeping the version sourced from the build.
    buildInfo()
}

// Only the executable bootJar is consumed; drop the redundant plain library jar.
tasks.named("jar") {
    enabled = false
}

// The test suite signs and verifies JWTs with its own throwaway secret, independent of the application's
// dev default in application.yaml (and of any secret a real deployment supplies).
tasks.test {
    systemProperty("jwt.secret", "test-only-hs256-secret-not-used-outside-the-test-suite")
}

// Cross-module mutation: mutate the api and data classes against this module's system and
// acceptance tests, the only tests that exercise the controllers. Opt-in and local:
// `gradle :application:pitest -Pmutation`.
configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.api.*", "de.seuhd.campuscoffee.data.*"))
    // The api/data production classes are Kotlin, so their bytecode is under classes/kotlin/main; the
    // only classes under classes/java/main are the kapt-generated *MapperImpl, which are excluded anyway.
    additionalMutableCodePaths.set(
        listOf(
            project(":api")
                .layout.buildDirectory
                .dir("classes/kotlin/main")
                .get()
                .asFile,
            project(":data")
                .layout.buildDirectory
                .dir("classes/kotlin/main")
                .get()
                .asFile
        )
    )
}
tasks.named("pitest") {
    dependsOn(":api:classes", ":data:classes")
}
