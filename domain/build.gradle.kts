import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("campuscoffee.java-conventions")
    id("campuscoffee.kotlin-conventions")
    id("campuscoffee.jacoco-conventions")
    id("campuscoffee.pitest-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.tx)
    implementation(libs.commons.lang3)
}

configure<PitestPluginExtension> {
    targetClasses.set(listOf("de.seuhd.campuscoffee.domain.*"))
}
