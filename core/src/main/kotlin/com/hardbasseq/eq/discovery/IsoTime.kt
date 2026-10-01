package com.hardbasseq.eq.discovery

// Upload timestamps as SoundCloud sends them ("2025-09-28T12:34:56Z", also the older
// "2025/09/28 12:34:56 +0000" form) to epoch milliseconds. Offsets and fractions are
// ignored - the values are UTC, and day precision is all the "recent uploads" logic
// needs.
object IsoTime {
    private val pattern = Regex("(\\d{4})[-/](\\d{2})[-/](\\d{2})[T ](\\d{2}):(\\d{2}):(\\d{2})")

    fun parseMillis(text: String?): Long? {
        val m = pattern.find(text.orEmpty()) ?: return null
        val (year, month, day, hour, minute, second) = m.destructured
        val y = year.toInt()
        val mo = month.toInt()
        val d = day.toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        val days = daysFromCivil(y, mo, d)
        return ((days * 24 + hour.toInt()) * 60 + minute.toInt()) * 60_000L + second.toInt() * 1000L
    }

    // Days since 1970-01-01 for a civil date (Howard Hinnant's algorithm).
    private fun daysFromCivil(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
