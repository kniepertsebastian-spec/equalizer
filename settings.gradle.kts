pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
    }
}

rootProject.name = "HardBassEQ"

include(":app")
include(":core")
include(":desktop")

// SC Equalizer Player: companion app that plays SoundCloud (and, later,
// YouTube) so HardBass EQ has a cooperating session to attach to - see
// player/README or roadmap.md for why this exists. Originally its own repo
// (github.com/kniepertsebastian-spec/Audioplayer), merged in with full
// history under player/ so both apps are developed and released together.
include(":player")
project(":player").projectDir = file("player/app")
