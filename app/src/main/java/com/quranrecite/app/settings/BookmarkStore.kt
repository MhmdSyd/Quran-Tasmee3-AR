package com.quranrecite.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The single saved reading bookmark: one ayah. */
data class Bookmark(val surah: Int, val ayah: Int) {
    val isValid: Boolean get() = surah in 1..114 && ayah >= 1
}

interface BookmarkStore {
    val bookmark: StateFlow<Bookmark?>
    /** Replaces any previous bookmark. Invalid values are ignored. */
    fun save(bookmark: Bookmark)
    fun clear()
}

/** SharedPreferences-backed; local only. */
class PrefsBookmarkStore(context: Context) : BookmarkStore {
    private val prefs = context.getSharedPreferences("bookmark", Context.MODE_PRIVATE)
    private val _bookmark = MutableStateFlow(load())
    override val bookmark: StateFlow<Bookmark?> = _bookmark.asStateFlow()

    override fun save(bookmark: Bookmark) {
        if (!bookmark.isValid) return
        _bookmark.value = bookmark
        prefs.edit().putInt("surah", bookmark.surah).putInt("ayah", bookmark.ayah).apply()
    }

    override fun clear() {
        _bookmark.value = null
        prefs.edit().clear().apply()
    }

    private fun load(): Bookmark? =
        Bookmark(prefs.getInt("surah", 0), prefs.getInt("ayah", 0)).takeIf { it.isValid }
}
