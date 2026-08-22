import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:curriculum — skill graph and item generators. Pure Kotlin/JVM.
// See docs/04-ARCHITECTURE.md §3.
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
    implementation(libs.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    // Passthrough for the golden-corpus regeneration switch (docs/20-PHASE-2-SPEC.md §7, Stage 2.0).
    // Gradle does not forward -D to the test JVM on its own, and the switch is useless if it cannot be
    // reached from the command line.
    System.getProperty("tonic.golden.regenerate")?.let { systemProperty("tonic.golden.regenerate", it) }

    // Same reason :core:audio and :core:engine set this, and added after making the identical mistake
    // their build files already warn about. SungToleranceMeasurementTest's whole deliverable is the
    // band table it prints (docs/10-TESTING.md §5: measurement runs are "a report, not just a
    // pass/fail"), and it went green in run #41 with every line of that report discarded - which is
    // indistinguishable, from the log, from a measurement nobody ever took.
    testLogging {
        showStandardStreams = true
    }
}
