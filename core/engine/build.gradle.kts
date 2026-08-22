import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:engine — adaptation and scheduling. Pure Kotlin/JVM.
// See docs/04-ARCHITECTURE.md §3 and docs/07-ADAPTIVE-ENGINE.md.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// docs/01-PRODUCT-SPEC.md §5 criterion 8: >=85% line coverage on this module.
kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:curriculum"))
    implementation(libs.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()

    // Test stdout is a deliverable in these two modules, not noise. docs/10-TESTING.md §5 asks for
    // simulation and measurement output to be "treated as a report, not just a pass/fail", and Stage
    // 1.4's acceptance says outright to "report the measured convergence point". Gradle captures
    // stdout and discards it unless asked, so without this the reports are written and never read -
    // which is how the staircase convergence figure and the pitch-detector accuracy table both came
    // to exist without anyone being able to see them.
    testLogging {
        showStandardStreams = true
    }
}
