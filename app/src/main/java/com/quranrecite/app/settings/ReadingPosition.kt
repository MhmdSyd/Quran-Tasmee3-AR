package com.quranrecite.app.settings

import android.content.Context

/** Last mushaf page the user was on (reader or recitation), so the app reopens there after being closed. Local only. */
interface ReadingPosition {
    var lastPage: Int
}

object ReadingPositionRules {
    const val PAGE_COUNT = 604
    /** Anything missing or out of range falls back to page 1. */
    fun sanitize(page: Int?): Int = if (page != null && page in 1..PAGE_COUNT) page else 1
}

class PrefsReadingPosition(context: Context) : ReadingPosition {
    private val prefs = context.getSharedPreferences("reading_position", Context.MODE_PRIVATE)
    override var lastPage: Int
        get() = ReadingPositionRules.sanitize(prefs.getInt(KEY, 1))
        set(value) {
            val v = ReadingPositionRules.sanitize(value)
            if (prefs.getInt(KEY, -1) != v) prefs.edit().putInt(KEY, v).apply()
        }

    private companion object { const val KEY = "last_page" }
}
