import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Kotlin configuration for the modules: the Java 25 toolchain and target, the Spring all-open plugin
// (so @Component/@Transactional classes can be proxied), ktlint formatting/linting, and the Kotlin
// reflection library that Spring requires at runtime.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        // retain parameter names for Spring's @PathVariable/@RequestParam binding, as -parameters does for Java
        javaParameters.set(true)
    }
}

// Formatting follows the official Kotlin style; the rules and line length come from the root
// .editorconfig. The plugin wires ktlintCheck into `check`, so the format gate rides on the build.
configure<KtlintExtension> {
    version.set(libs.findVersion("ktlint-tool").get().requiredVersion)
}

dependencies {
    implementation(kotlin("reflect"))
}
