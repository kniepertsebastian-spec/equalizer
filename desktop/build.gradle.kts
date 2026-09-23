import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ktlint)
}

// Session 18: desktop companion for Windows/Linux. It does not reimplement
// system-wide audio DSP itself (no public cross-platform API for that) -
// instead it reuses the :core preset/curve logic to generate config files
// for two already-proven system-wide EQ engines the user installs
// separately: Equalizer APO (Windows) and EasyEffects (Linux, PipeWire).
// See desktop/README.md.
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
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.runtime)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.hardbasseq.eq.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "HardBassEQ"
            packageVersion = "0.2.0"
            description = "HardBass EQ desktop companion: presets for Equalizer APO / EasyEffects"
            vendor = "HardBass EQ"

            windows {
                menuGroup = "HardBass EQ"
                upgradeUuid = "9d5f6c9a-9f3a-4d3e-9f1f-6a2e0b6e6f3a"
            }
            linux {
                packageName = "hardbasseq"
            }
        }
    }
}
