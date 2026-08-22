// Root build file. Plugins are declared here with apply false and applied
// per-module so each module controls exactly what it pulls in.
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
}

// Every failing test names itself, everywhere, once.
//
// A test failure used to reach the log as one line - the test name, "FAILED", and a bare exception
// class - with the message, the assertion text and the stack trace all discarded. Six consecutive CI
// rounds were spent on this branch learning things a full stack trace states outright, at eight
// minutes a round, because the sandbox this is developed in has no Android SDK and CI is therefore
// the compiler as well as the test runner. Making the report complete is cheaper than one round.

// Below this, a test is not what is making a CI round slow. See the afterTest listener.
val slowTestThresholdMs = 1_000L

subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
            events("failed")
        }

        // Every slow test names itself too, for the same reason and at the same cost.
        //
        // Run #41 took 11m22s against a 5-9 minute norm after a batch of tests was added that drive
        // the real practice loop through real playback delays. Which of them is expensive was not
        // answerable from the log - Gradle reports no per-test duration - so the obvious next step was
        // to guess and refactor, on a repo where a wrong guess costs eleven minutes to discover. This
        // prints the answer instead.
        //
        // Threshold rather than every test: a full timing dump is thousands of lines nobody reads,
        // and anything under a second is not what makes a round slow. Printed from the Gradle process
        // rather than the test JVM, so it does not depend on a module setting showStandardStreams.
        val report =
            KotlinClosure2<TestDescriptor, TestResult, Unit>({ descriptor, result ->
                val elapsedMs = result.endTime - result.startTime
                if (elapsedMs >= slowTestThresholdMs) {
                    val owner = descriptor.className?.substringAfterLast('.') ?: "?"
                    println("[slow-test] ${elapsedMs.toString().padStart(6)}ms  $owner.${descriptor.name}")
                }
            })
        afterTest(report)
    }
}

tasks.register("jvmTestAll") {
    group = "verification"
    description = "Runs all JVM unit tests across the pure Kotlin core modules — the fast, no-emulator subset."
    dependsOn(
        ":core:model:test",
        ":core:curriculum:test",
        ":core:engine:test",
    )
}
