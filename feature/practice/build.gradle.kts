import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:practice placeholder
// See docs/08-UI-SPEC.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// Everything currently in this module (PracticeLoopEngine + its data classes) is plain,
// Android-framework-free logic exercised end to end by PracticeLoopEngineTest - no thin device
// wrapper to exclude yet, unlike :core:audio/:core:data. ModuleMarker is the one exception: a
// content-free Stage 0 placeholder object, excluded the same way :core:data excludes pure DI wiring.
kover {
    reports {
        filters {
            excludes {
                classes("com.tonic.feature.practice.ModuleMarker")
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
    namespace = "com.tonic.feature.practice"
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
    implementation(project(":core:curriculum"))
    implementation(project(":core:engine"))
    implementation(project(":core:audio"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
    // PracticeLoopEngineTest drives full sessions (up to ~200 simulated minutes, thousands of items)
    // through the real curriculum/engine reduction to prove replayability end to end - the default
    // forked-JVM heap isn't enough headroom for that.
    maxHeapSize = "2g"
}
