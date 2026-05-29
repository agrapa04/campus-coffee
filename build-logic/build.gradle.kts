plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// The convention plugins under src/main/kotlin apply these third-party plugins by id, so their
// implementation artifacts must be on build-logic's classpath. Versions come from the catalog.
dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.dependency.management.plugin)
    implementation(libs.pitest.gradle.plugin)
}
