// Replaces the Maven `coverage` module: aggregates every module's JaCoCo execution data into one
// report and enforces the coverage gate (90% line, 80% branch) with the same exclusions.
plugins {
    base
    jacoco
    id("jacoco-report-aggregation")
    // The aggregation configuration resolves the modules' transitive runtime dependencies, whose
    // versions are supplied by the Spring Boot BOM (io.spring.dependency-management does not export
    // its managed versions to consumers, so the BOM must be imported here too).
    alias(libs.plugins.dependency.management)
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.spring.cloud.get()}")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencies {
    jacocoAggregation(project(":domain"))
    jacocoAggregation(project(":api"))
    jacocoAggregation(project(":data"))
    jacocoAggregation(project(":application"))
}

reporting {
    reports {
        create<JacocoCoverageReport>("testCodeCoverageReport") {
            testSuiteName.set("test")
        }
    }
}

val aggregateReport = tasks.named<JacocoReport>("testCodeCoverageReport") {
    reports {
        xml.required.set(true)
        csv.required.set(true)
    }
}

// Gate exclusions, identical to the Maven coverage module:
//   TestFixtures (test data shipped in domain/src/main), startup wiring (Application/LoadInitialData),
//   and the generated MapStruct *MapperImpl classes.
val gateExclusions = listOf(
    "de/seuhd/campuscoffee/domain/tests/**",
    "**/Application.*",
    "**/LoadInitialData.*",
    "**/*MapperImpl.*",
)

val coverageGate = tasks.register<JacocoCoverageVerification>("coverageGate") {
    group = "verification"
    description = "Fails the build when aggregate line/branch coverage is below the gate."
    executionData.from(aggregateReport.map { it.executionData })
    sourceDirectories.from(aggregateReport.map { it.sourceDirectories })
    classDirectories.from(aggregateReport.map { it.classDirectories.asFileTree.matching { exclude(gateExclusions) } })
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(aggregateReport, coverageGate)
}
