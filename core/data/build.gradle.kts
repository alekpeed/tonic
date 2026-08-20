import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:data — persistence. Android library: Room + DataStore. Repositories
// expose only :core:model domain types; Room entities never escape this
// module. See docs/04-ARCHITECTURE.md §3 and docs/05-DATA-MODEL.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// Unlike :core:audio, nothing here needs a real device - Room and DataStore both run against
// Robolectric-backed in-memory/temp-file instances on the JVM, so the coverage floor applies to
// nearly the whole module. Excluded: `di` (plain Hilt @Module/@Provides/@Binds wiring: no branches,
// no logic - a real dependency-injection container is what actually exercises it, and every type it
// wires already has its own direct-construction tests) and every Room/Hilt *_Impl / *_Factory /
// codegen class - generated boilerplate this build didn't write, whose correctness the functional
// repository tests already verify indirectly (a wrong generated DAO impl would fail those tests; a
// kover line-coverage count of its internal cursor/connection-retry branches would not add signal).
kover {
    reports {
        filters {
            excludes {
                packages("com.tonic.core.data.di")
                classes(
                    "hilt_aggregated_deps.*",
                    "*_HiltModules*",
                    "*_Factory",
                    "*_MembersInjector",
                    "*_Impl",
                    "*_Impl\$*",
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
    namespace = "com.tonic.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.room.testing)

    // Test-only: the rebuildFromAttempts() acceptance test (docs/09-BUILD-PLAN.md Stage 5) needs a real
    // SkillStateReplayer to be meaningful, not a stub - :core:engine's SkillStateReducer is that real
    // implementation. This does NOT violate docs/04-ARCHITECTURE.md §2's layering: that rule governs the
    // production dependency graph, and testImplementation never reaches the main sourceSet or the
    // shipped app; the composition root (:app) is what wires SkillStateReplayer to SkillStateReducer at
    // runtime, exactly like DependencyDirectionTest.kt only scans main sourceSets in the pure modules.
    testImplementation(project(":core:engine"))
    testImplementation(project(":core:curriculum"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}
