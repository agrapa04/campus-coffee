import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("de.seuhd.campuscoffee.java-conventions")
    id("de.seuhd.campuscoffee.kotlin-conventions")
    id("de.seuhd.campuscoffee.kotlin-jpa-conventions")
    id("de.seuhd.campuscoffee.kotlin-kapt-conventions")
    id("de.seuhd.campuscoffee.jacoco-conventions")
    id("de.seuhd.campuscoffee.pitest-conventions")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.spring.boot.starter.data.jpa)
    // spring-web provides RestClient and the declarative @HttpExchange client used for the OSM API.
    implementation(libs.spring.web)
    implementation(libs.postgresql)
    implementation(libs.spring.boot.flyway)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation(libs.jackson.dataformat.xml)
    // Jackson 3 Kotlin module for the event-sourcing JSON bodies (Jackson 3 databind bundles java.time).
    implementation(libs.jackson3.module.kotlin)
    // BCrypt/delegating password encoder for the PasswordHasher adapter (small, dependency-free).
    implementation(libs.spring.security.crypto)
    // The OSM client and the event-sourcing startup runners log via KotlinLogging.logger {} (over SLF4J).
    implementation(libs.kotlin.logging)

    // MapStruct is compile-only for the Kotlin mappers; kapt runs the processor that generates the impls.
    compileOnly(libs.mapstruct)
    testImplementation(libs.mapstruct)
    kapt(libs.mapstruct.processor)

    testImplementation(libs.testcontainers.postgresql)
}

configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.data.*"))
}
