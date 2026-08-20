import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:model — domain vocabulary. Pure Kotlin/JVM, stdlib only (plus
// kotlinx-serialization for the @Serializable state classes). See
// docs/04-ARCHITECTURE.md §3. Must never depend on android.* / androidx.*;
// enforced by DependencyDirectionTest in this module's own test source set.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// docs/01-PRODUCT-SPEC.md §5 success criterion 8 / docs/09-BUILD-PLAN.md
// Stage 1: >=90% line coverage on this module, enforced, not just measured.
kover {
    reports {
        verify {
            rule {
                minBound(90)
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
    implementation(libs.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
