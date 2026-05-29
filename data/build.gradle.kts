import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("campuscoffee.java-conventions")
    id("campuscoffee.kotlin-conventions")
    id("campuscoffee.kotlin-jpa-conventions")
    id("campuscoffee.kotlin-kapt-conventions")
    id("campuscoffee.jacoco-conventions")
    id("campuscoffee.pitest-conventions")
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

    // MapStruct is compile-only for the Kotlin mappers; kapt runs the processor that generates the impls.
    compileOnly(libs.mapstruct)
    testImplementation(libs.mapstruct)
    kapt(libs.mapstruct.processor)

    testImplementation(libs.testcontainers.postgresql)
}

configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.data.*"))
}
