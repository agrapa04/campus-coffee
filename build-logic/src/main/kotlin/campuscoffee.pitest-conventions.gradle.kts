import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// Opt-in, local mutation testing (run with `-Pmutation`, e.g. `gradle :domain:pitest -Pmutation`).
// Mirrors the Maven `mutation` profile's shared config; each module sets its own targetClasses.
// Not part of `build`/CI. The application module's cross-module run (mutating api.*/data.* against
// the system tests) is a follow-up; this covers the per-module runs.
plugins {
    id("info.solidsoft.pitest")
}

val libs = the<VersionCatalogsExtension>().named("libs")

configure<PitestPluginExtension> {
    pitestVersion.set(libs.findVersion("pitest-tool").get().requiredVersion)
    junit5PluginVersion.set(libs.findVersion("pitest-junit5").get().requiredVersion)
    targetTests.set(listOf("de.seuhd.campuscoffee.*"))
    excludedClasses.set(
        listOf(
            "de.seuhd.campuscoffee.domain.tests.*",
            "de.seuhd.campuscoffee.Application",
            "de.seuhd.campuscoffee.LoadInitialData",
            "de.seuhd.campuscoffee.*.*MapperImpl",
        )
    )
    mutators.set(listOf(providers.gradleProperty("pitest.mutators").orElse("DEFAULTS").get()))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(false)
    threads.set(1)
    timeoutConstInMillis.set(30000)
    jvmArgs.set(listOf("-XX:+EnableDynamicAgentLoading", "-Xshare:off"))
}
