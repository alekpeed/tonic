// Root build file. Plugins are declared here with apply false and applied
// per-module so each module controls exactly what it pulls in.
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
subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
            events("failed")
        }
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
