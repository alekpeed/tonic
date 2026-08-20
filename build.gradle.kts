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

tasks.register("jvmTestAll") {
    group = "verification"
    description = "Runs all JVM unit tests across the pure Kotlin core modules — the fast, no-emulator subset."
    dependsOn(
        ":core:model:test",
        ":core:curriculum:test",
        ":core:engine:test",
    )
}
