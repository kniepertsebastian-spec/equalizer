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
-keepclassmembers class com.hardbasseq.eq.** {
    *** Companion;
}
-keepclasseswithmembers class com.hardbasseq.eq.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hardbasseq.eq.**$$serializer { *; }
-keep,includedescriptorclasses class com.soundcloud.equalizer.player.**$$serializer { *; }

# Room entities and the schema JSON are read by generated code; Room's own rules cover the rest.
-keep class com.hardbasseq.eq.data.** { *; }
