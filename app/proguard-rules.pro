# R8 rules for the release build (roadmap 06, P6-6).
#
# Most libraries here (Hilt, Room, Coil, OkHttp, Media3, WorkManager, kotlinx.serialization) ship
# their own consumer rules, and everything declared in the manifest is kept by AAPT. What is left is
# what is reached by name at run time.

# Stack traces in bug reports and the diagnostics report stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: the generated serializers of our own models are found by name
# (Json.decodeFromString(Preset.serializer(), ...) is direct, but nested/polymorphic lookups are not).
-keepclassmembers class com.auracurve.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.auracurve.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.auracurve.app.**$$serializer { *; }
-keep,includedescriptorclasses class com.auracurve.app.player.**$$serializer { *; }

# Room entities and the schema JSON are read by generated code; Room's own rules cover the rest.
-keep class com.auracurve.app.data.** { *; }

# Roadmap 12, P12-1: no verbose/debug/info logging in the release. Log.w and Log.e stay for the
# diagnostics (they carry exception class names, never tokens - see RemoteToken / TokenVault).
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Ktor (flavor "full", the local remote-control server): its IntelliJ debugger detector reaches for
# java.lang.management, which Android does not have. Only called under a JVM debugger, never here.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# scripts/check_store_apk.py finds the full flavor's online code partly by class name. R8 would rename
# those classes, and a store build that wrongly contained one would pass the check. Their names stay
# (no effect on the store flavor, which does not have these classes at all).
-keepnames class **.SoundCloudLoginActivity
-keepnames class **.SpotifyLoginActivity
-keepnames class **.SpotifyApiClient
-keepnames class **.ReleaseUpdateClient
-keepnames class **.SpotifyImportPlan
-keepnames class **.SoundCloudLinkImport
-keepnames class **.RemoteServerService
-keepnames class **.RemoteNotificationListener
-keepnames class **.MdnsResponder
