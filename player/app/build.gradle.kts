import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.soundcloud.equalizer.player"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Robolectric (ResolvingTrackMediaSourceTest runs a real ExoPlayer) needs the resources.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        // Matches the :app module - the JDK running Gradle isn't the bytecode target.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Pure-Kotlin DSP (BassExciterPcm16) used by the virtual-bass audio processor.
    implementation(project(":core"))

    implementation(libs.core.ktx.player)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.webkit)

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    // HLS streams: SoundCloud offers some full-length (Go) streams only that way.
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    // MediaLibraryService: notification, lock screen, Bluetooth, Wear and Android Auto from one session.
    implementation(libs.media3.session)

    // Networking / Async / JSON
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // Android ships org.json; the JVM unit tests run against stubs, so they need a real one.
    testImplementation(libs.org.json)
    testImplementation(libs.mockito.core)
    // A real ExoPlayer with fake renderers and clock for the queue's stream lookup; Robolectric
    // in the version media3-test-utils-robolectric 1.11.1 is built against.
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
