import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:ui — design system: theme, typography, spacing tokens, shared
// composables (including the degree ladder). See docs/08-UI-SPEC.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// Composables can't be meaningfully unit-tested on the JVM without a real composition (Robolectric +
// ComposeTestRule) - out of scope for this module the same way :core:audio excludes its
// device-dependent wrappers. `labels/` is plain, non-Compose logic and is held to the same bar as
// :core:model since it's exactly that kind of pure mapping.
kover {
    reports {
        filters {
            excludes {
                packages("com.tonic.core.ui.theme", "com.tonic.core.ui.ladder", "com.tonic.core.ui.components")
                classes("com.tonic.core.ui.ModuleMarker")
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

android {
    namespace = "com.tonic.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Already provisioned for :core:data and every :feature:* module - wired in here so the theme's
    // *dynamic* color path can be exercised against a real Context, which is the only way to test what
    // the app actually renders on Android 12+ (docs/08-UI-SPEC.md §8).
    testImplementation(libs.androidx.test.ext.junit)
    testRuntimeOnly(libs.junit.vintage.engine)

    // Compose UI testing on the JVM under Robolectric, not only on a device. These were previously
    // androidTest-only, which meant docs/09-BUILD-PLAN.md Stage 7's "ladder fits seven degrees plus
    // gaps on a 5-inch screen, no scroll, at 200% font scale" and docs/08-UI-SPEC.md §9's
    // maximum-font-scale requirement had no way to run in this project at all - and in fact never ran.
    // A layout claim that cannot be executed is a claim nobody is checking.
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}
