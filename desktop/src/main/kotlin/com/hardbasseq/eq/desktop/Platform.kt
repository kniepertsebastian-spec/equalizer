package com.hardbasseq.eq.desktop

enum class DesktopPlatform { WINDOWS, LINUX, OTHER }

fun detectPlatform(osNameProperty: String = System.getProperty("os.name") ?: ""): DesktopPlatform {
    val name = osNameProperty.lowercase()
    return when {
        name.contains("win") -> DesktopPlatform.WINDOWS
        name.contains("nux") || name.contains("nix") -> DesktopPlatform.LINUX
        else -> DesktopPlatform.OTHER
    }
}

/** Best-effort default Equalizer APO config folder on Windows. */
fun defaultEqualizerApoConfigDir(): String {
    val programFiles = System.getenv("ProgramFiles") ?: """C:\Program Files"""
    return "$programFiles\\EqualizerAPO\\config"
}
