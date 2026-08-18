import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:model — domain vocabulary. Pure Kotlin/JVM, stdlib only (plus
// kotlinx-serialization for the @Serializable state classes). See
// docs/04-ARCHITECTURE.md §3. Must never depend on android.* / androidx.*;
// enforced by DependencyDirectionTest in this module's own test source set.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
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
    implementation(libs.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
