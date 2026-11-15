package com.quranrecite.app.settings

/**
 * How closely a recognised word must resemble the expected Quran word (see `recitation/ArabicMatch.kt`).
 * Lives in its own file (no Android imports) so the pure-JVM matcher and its tests can use it.
 */
enum class Strictness { Lenient, Normal, Strict }
