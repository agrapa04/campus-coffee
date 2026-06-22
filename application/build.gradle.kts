import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("de.seuhd.campuscoffee.java-conventions")
    id("de.seuhd.campuscoffee.kotlin-conventions")
    id("de.seuhd.campuscoffee.jacoco-conventions")
    id("de.seuhd.campuscoffee.pitest-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api"))
    // Compile-scoped (not runtimeOnly): puts the data module's Spring configuration metadata on this
    // module's compile classpath, so the IDE resolves the campus-coffee.* keys in application.yaml.
    implementation(project(":data"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    // Spring Security (HTTP Basic + the filter chain) and OAuth2 resource server (JWT bearer tokens).
    // The starter ships a working-but-permissive setup; the assignment tightens it.
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    // The fixture startup loader logs via KotlinLogging.logger {} (over SLF4J).
    implementation(libs.kotlin.logging)

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
    systemProperty("campus-coffee.jwt.secret", "test-only-hs256-secret-not-used-outside-the-test-suite")

    // Run the system, acceptance, and architecture tests across several JVM processes. Process-level
    // forking is the only safe form of parallelism here: SystemTestUtils is an `object` with shared
    // mutable state (the RestTestClient) and the test bases wipe the whole database between tests with
    // clearAll(), so two tests must never run concurrently against the same JVM or database. Each fork is
    // a separate JVM, so it gets its own SystemTestUtils and its own Testcontainers PostgreSQL instance
    // (the container lives in a companion object, one per JVM), and JUnit runs the classes within a fork
    // serially, so two tests never touch the shared client at once and clearAll() never wipes a running
    // test's data. Leaving forkEvery at its default (0) reuses each fork JVM across the classes it runs,
    // so Spring's per-JVM context cache still pays off within the fork. Each concurrent fork boots a Spring
    // context and starts a PostgreSQL container, both of which cost memory, so the fork count is capped;
    // override it with -PtestForks=N (1 disables parallelism, e.g. on a small CI runner).
    maxParallelForks =
        (project.findProperty("testForks") as String?)?.toInt()
            ?: (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

    // Cap each fork's heap so the forks cannot collectively overcommit memory.
    maxHeapSize = "1g"
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
