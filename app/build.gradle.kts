import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.tonic.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tonic.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Every debug build is signed by the keystore committed at keystore/debug.keystore, rather
        // than by whatever ~/.android/debug.keystore the building machine happens to have generated
        // for itself.
        //
        // Android refuses to update an installed app whose signing certificate has changed, and with
        // per-machine keys that is every combination: a CI build will not install over a local one,
        // one laptop's build will not install over another's, and a fresh CI runner would eventually
        // stop installing over its own earlier output. The only way through is to uninstall first,
        // which wipes the very progress a build is usually being installed to inspect.
        //
        // The key is deliberately in the repository and is not a secret. A debug key signs nothing
        // that matters: it cannot publish to Play, it grants no access to anything, and Android's own
        // default debug key uses these exact well-known credentials (android/android/androiddebugkey)
        // for the same reason. Release signing is a separate, unbuilt concern and must never point
        // here.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        // Gates the debug-only intake skip in NavGraph. The skip has to live here rather than in
        // :feature:diagnostic, because :app is what decides navigation and what knows the build type.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    implementation(project(":feature:diagnostic"))
    implementation(project(":feature:practice"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:settings"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}
