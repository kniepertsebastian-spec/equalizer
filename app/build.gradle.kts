import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
}

android {
    // Roadmap M1: single source of truth is gradle.properties, not this file.
    namespace = providers.gradleProperty("hardbasseq.namespace").get()
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("hardbasseq.applicationId").get()
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0-m1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests {
            // android.jar's real classes are stubbed out (throw
            // "not mocked") under plain testDebugUnitTest by default - this repo
            // has code (e.g. RoomPresetRepository's corrupted-row logging) that
            // legitimately calls android.util.Log from a path unit tests exercise,
            // with no Robolectric/instrumentation set up to provide a real
            // implementation. Falling back to harmless defaults (Log.w() no-ops
            // and returns 0) instead of throwing is the standard fix.
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        // With built-in Kotlin disabled, match Java's target explicitly.
        // The JDK running Gradle (21 in CI) is not the bytecode target.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    // Matches the ktlint_official style already implied by
    // kotlin.code.style=official in gradle.properties.
    android.set(true)
}

ksp {
    // M2: exports the DB schema to JSON on every build, the baseline
    // MigrationTestHelper tests need. The `presets` table has never had a real
    // write path before this change (verified: nothing referenced PresetDao),
    // so there's no real prior schema to migrate away from yet - this starts
    // the practice from the first schema that's actually reachable. No
    // MigrationTestHelper test exists yet either (needs Robolectric or
    // instrumentation, neither set up in this repo - see AppDatabase.kt).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Shared preset/DSP domain logic - also used by the :desktop module.
    implementation(project(":core"))
    // SC Equalizer Player: the companion source screen the master bar's source
    // picker launches (see PlayerBridge). A library module now, merged into this
    // single app's manifest/APK instead of its own separate app.
    implementation(project(":player"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
