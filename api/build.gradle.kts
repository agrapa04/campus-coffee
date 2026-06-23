import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("de.seuhd.campuscoffee.java-conventions")
    id("de.seuhd.campuscoffee.kotlin-conventions")
    id("de.seuhd.campuscoffee.kotlin-kapt-conventions")
    id("de.seuhd.campuscoffee.jacoco-conventions")
    id("de.seuhd.campuscoffee.pitest-conventions")
}

dependencies {
    // api re-exposes domain types in its public signatures (e.g., CrudController<PosDto, Pos, Long>).
    api(project(":domain"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.spring.boot.starter.validation)
    // The api layer owns the Spring Security setup, because the access rules are part of the interface
    // this layer exposes: the filter chain and access rules (SecurityConfig), the JWT encoder and decoder
    // (JwtConfig), the UserDetailsService, the principal lookup (CurrentUserProvider), and the JSON 401
    // writer. ArchUnit gates layers, not libraries, so the Spring Security dependency here is allowed; the
    // domain stays free of it.
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
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
