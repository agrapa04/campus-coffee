import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.process.CommandLineArgumentProvider

// Shared Java configuration for the library/application modules: toolchain, the Spring Boot and
// Spring Cloud BOMs, Lombok + JSpecify + the Spring config processor, and the test JVM args that
// the Maven surefire plugin used (JaCoCo's agent is added by campuscoffee.jacoco-conventions).
plugins {
    `java-library`
    id("io.spring.dependency-management")
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "de.seuhd.campuscoffee"
version = "0.0.5"

// Override the Spring Boot BOM's JUnit version (as the Maven build did) so junit-bom and thus the
// junit-platform artifacts resolve to a version compatible with cucumber-junit-platform-engine
// (which requires junit-platform 1.14.x); the 3.5.8 BOM would otherwise pin 1.12.x.
extra["junit-jupiter.version"] = libs.findVersion("junit-jupiter").get().requiredVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

// Retain method parameter names (Spring Boot's Maven parent enables this by default). Without it,
// Spring cannot bind @PathVariable/@RequestParam by name and returns HTTP 400.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.findVersion("spring-boot").get().requiredVersion}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.findVersion("spring-cloud").get().requiredVersion}")
    }
}

dependencies {
    // Test stack shared by every module: JUnit 5 / Mockito / AssertJ via the starter, plus the
    // JUnit Platform launcher Gradle needs on the test runtime classpath (the Spring Boot BOM
    // manages its version but does not add the dependency). Production dependencies that only some
    // modules use (spring-boot-starter-web, commons-lang3) live in those modules' build files.
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())

    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())

    compileOnly(libs.findLibrary("jspecify").get())
    testCompileOnly(libs.findLibrary("jspecify").get())

    annotationProcessor(libs.findLibrary("spring-boot-configuration-processor").get())
}

// Mockito as a javaagent, matching the Maven surefire config. A dedicated, non-transitive
// configuration resolves just the mockito-core jar (its premain is the agent).
val mockitoAgent = configurations.create("mockitoAgent") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    "mockitoAgent"(libs.findLibrary("mockito-core").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    })
}
