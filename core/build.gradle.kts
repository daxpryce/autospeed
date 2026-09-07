import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}

kover {
    reports {
        verify {
            rule("core line coverage") {
                minBound(100, coverageUnits = CoverageUnit.LINE)
            }
            rule("core branch coverage") {
                minBound(100, coverageUnits = CoverageUnit.BRANCH)
            }
        }
    }
}
