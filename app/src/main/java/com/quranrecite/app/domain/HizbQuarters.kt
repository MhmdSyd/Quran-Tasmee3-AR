package com.quranrecite.app.domain

/** Where in its hizb an ayah lies: [hizb] 1..60 and [part] 0 = start of the hizb, 1 = a quarter, 2 = a half, 3 = three quarters. */
data class HizbLabel(val hizb: Int, val part: Int, val quarter: Int) {
    /** Juz' 1..30 of this quarter: every juz is 8 quarters and starts on a hizb start (checked in [HizbQuarters.problem]). */
    val juz: Int get() = (quarter - 1) / 8 + 1
}

/**
 * The 240 hizb-quarter (rub' al-hizb) starts of the Madani Mushaf as (surah, ayah), in reading order.
 * Quarter q (1..240) belongs to hizb (q-1)/4 + 1; part (q-1)%4: 0 = the hizb starts here, 1 = 1/4, 2 = 1/2, 3 = 3/4 passed.
 * Anything other than a complete, ordered list of 240 is rejected ([from] returns null) so the app never shows a wrong hizb.
 */
class HizbQuarters private constructor(private val starts: List<Pair<Int, Int>>) {

    private fun key(s: Int, a: Int) = s * 1000 + a

    /** Quarter (1..240) the ayah lies in: the last quarter start at or before it. */
    fun quarterAt(surah: Int, ayah: Int): Int? {
        val k = key(surah, ayah)
        var lo = 0; var hi = starts.size - 1; var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (key(starts[mid].first, starts[mid].second) <= k) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (found < 0) null else found + 1
    }

    fun labelAt(surah: Int, ayah: Int): HizbLabel? = quarterAt(surah, ayah)?.let { labelOf(it) }

    companion object {
        const val QUARTERS = 240

        fun labelOf(quarter: Int) = HizbLabel(hizb = (quarter - 1) / 4 + 1, part = (quarter - 1) % 4, quarter = quarter)

        /** Why [starts] is not a valid list, or null when it is valid. Checks: 240 entries, strictly increasing, 1:1 first, every juz start is a hizb start (quarters 1, 9, 17 ...). */
        fun problem(starts: List<Pair<Int, Int>>): String? {
            if (starts.size != QUARTERS) return "expected $QUARTERS quarters, found ${starts.size}"
            if (starts.first() != (1 to 1)) return "the first quarter must start at 1:1"
            for (i in 1 until starts.size) {
                val p = starts[i - 1]; val c = starts[i]
                if (c.first * 1000 + c.second <= p.first * 1000 + p.second) return "quarters are not strictly increasing at #${i + 1}"
            }
            for ((j, juz) in JuzStarts.FIRST_AYAH.withIndex()) {
                if (starts[8 * j] != juz) return "juz ${j + 1} should start quarter ${8 * j + 1} at $juz but it is ${starts[8 * j]}"
            }
            return null
        }

        fun from(starts: List<Pair<Int, Int>>): HizbQuarters? = if (problem(starts) == null) HizbQuarters(starts) else null
    }
}

/** Spoken description for accessibility. */
fun HizbLabel.description(): String = when (part) {
    0 -> "بداية الحزب"
    1 -> "ربع الحزب"
    2 -> "نصف الحزب"
    else -> "ثلاثة أرباع الحزب"
}
