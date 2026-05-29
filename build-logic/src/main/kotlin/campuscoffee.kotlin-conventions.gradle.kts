import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Kotlin configuration for converted modules: the Java 25 toolchain and target, the Spring all-open
// plugin (so @Component/@Transactional classes can be proxied), the Lombok plugin (so Kotlin can
// read the Lombok-generated builders/accessors on the not-yet-converted Java models in the same
// module), and the Kotlin reflection library that Spring requires at runtime.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.lombok")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        // retain parameter names for Spring's @PathVariable/@RequestParam binding, as -parameters does for Java
        javaParameters.set(true)
    }
}

dependencies {
    implementation(kotlin("reflect"))
}
