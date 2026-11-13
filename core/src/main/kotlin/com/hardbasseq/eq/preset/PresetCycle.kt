package com.hardbasseq.eq.preset

/** Stepping through presets one by one, as the quick-settings tile and the home-screen widget do. */
object PresetCycle {
    /**
     * The id after [currentId] in [ids], wrapping around. If [currentId] is not in the list (a deleted
     * custom preset, edited settings) the cycle starts at the first entry; null for an empty list.
     */
    fun nextId(
        ids: List<String>,
        currentId: String,
    ): String? {
        if (ids.isEmpty()) return null
        val index = ids.indexOf(currentId)
        return ids[(index + 1) % ids.size]
    }
}
