package com.quranrecite.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecitationSettings(
    val strictness: Strictness = Strictness.Normal,
    val fontScale: Float = 1f,
    val showMistakeImmediately: Boolean = true,
    /** تسميع مستمر: when a page is completed, go on to the next page and keep reciting instead of showing the results. */
    val continuousRecitation: Boolean = false,
)

interface SettingsRepository {
    val settings: StateFlow<RecitationSettings>
    fun update(transform: (RecitationSettings) -> RecitationSettings)
}

/** SharedPreferences-backed; local only. */
class PrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("recitation_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<RecitationSettings> = _settings.asStateFlow()

    override fun update(transform: (RecitationSettings) -> RecitationSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString("strictness", next.strictness.name)
            .putFloat("fontScale", next.fontScale)
            .putBoolean("showMistakeImmediately", next.showMistakeImmediately)
            .putBoolean("continuousRecitation", next.continuousRecitation)
            .apply()
    }

    private fun load(): RecitationSettings {
        val d = RecitationSettings()
        return RecitationSettings(
            strictness = runCatching { Strictness.valueOf(prefs.getString("strictness", "") ?: "") }
                .getOrDefault(d.strictness),
            fontScale = prefs.getFloat("fontScale", d.fontScale),
            showMistakeImmediately = prefs.getBoolean("showMistakeImmediately", d.showMistakeImmediately),
            continuousRecitation = prefs.getBoolean("continuousRecitation", d.continuousRecitation),
        )
    }
}
