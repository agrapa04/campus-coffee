import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("de.seuhd.campuscoffee.java-conventions")
    id("de.seuhd.campuscoffee.kotlin-conventions")
    id("de.seuhd.campuscoffee.kotlin-kapt-conventions")
    id("de.seuhd.campuscoffee.jacoco-conventions")
    id("de.seuhd.campuscoffee.pitest-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.tx)

    // Generate Spring configuration metadata for the @ConfigurationProperties classes (ApprovalConfiguration);
    // the processor runs via kapt.
    kapt(libs.spring.boot.configuration.processor)
}

configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.domain.*"))
}
