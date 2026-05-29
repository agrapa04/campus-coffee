import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("campuscoffee.java-conventions")
    id("campuscoffee.kotlin-conventions")
    id("campuscoffee.kotlin-kapt-conventions")
    id("campuscoffee.jacoco-conventions")
    id("campuscoffee.pitest-conventions")
}

dependencies {
    // api re-exposes domain types in its public signatures (e.g. CrudController<PosDto, Pos, Long>).
    api(project(":domain"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.spring.boot.starter.validation)
    // Jackson support for the Kotlin DTOs (construction, nullability, defaults); Spring Boot auto-registers it.
    implementation(libs.jackson.module.kotlin)

    // MapStruct is compile-only for the Kotlin mappers; kapt runs the processor that generates the impls.
    compileOnly(libs.mapstruct)
    testImplementation(libs.mapstruct)
    kapt(libs.mapstruct.processor)
}

configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.api.*"))
}
