// IntelliJ flags this `repositories` block as unstable: dependencyResolutionManagement is an
// @Incubating Gradle API, and there is no stable alternative for declaring repositories centrally.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
