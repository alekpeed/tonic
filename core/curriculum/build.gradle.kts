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
}
