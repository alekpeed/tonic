import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:audio — synthesis and playback. Android library (needs
// android.media.AudioTrack), but the rendering functions themselves are
// pure FloatArray-in/FloatArray-out and unit-tested on the JVM without a
// device. See docs/04-ARCHITECTURE.md §3 and docs/06-AUDIO-ENGINE.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// docs/01-PRODUCT-SPEC.md §5 criterion 8 sets a coverage floor for
// :core:model/:core:curriculum/:core:engine specifically, not :core:audio —
// this module mixes pure render logic (fully testable on the JVM) with
// thin Android wrappers (AudioTrackPlayer, AudioFocusManager) that need a
// real device to exercise meaningfully and are excluded here rather than
// padded with tests that would just be asserting mocks called each other.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.tonic.core.audio.player.*",
                    "com.tonic.core.audio.focus.*",
                )
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

android {
    namespace = "com.tonic.core.audio"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
    // Passthrough for the listenable-sample export (AudioSampleExportTest). Gradle does not forward
    // -D to the test JVM on its own, and an opt-in switch is useless if it cannot be reached from the
    // command line - the same reasoning as the golden-corpus switch in :core:curriculum.
    System.getProperty("tonic.audio.export")?.let { systemProperty("tonic.audio.export", it) }
}
