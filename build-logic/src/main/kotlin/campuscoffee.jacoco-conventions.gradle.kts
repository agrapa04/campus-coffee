import org.gradle.api.artifacts.VersionCatalogsExtension

// Applies JaCoCo so each module records execution data (build/jacoco/test.exec). The combined
// report and the coverage gate live in the :coverage subproject (jacoco-report-aggregation).
plugins {
    jacoco
}

val libs = the<VersionCatalogsExtension>().named("libs")

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}
