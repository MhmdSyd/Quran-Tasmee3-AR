package com.quranrecite.app.di

import android.content.Context
import com.quranrecite.app.data.QuranRepository
import com.quranrecite.app.data.StubQuranRepository
import com.quranrecite.app.recitation.NoOpRecitationMatcher
import com.quranrecite.app.recitation.RecitationMatcher
import com.quranrecite.app.session.InMemorySessionStore
import com.quranrecite.app.session.SessionStore
import com.quranrecite.app.settings.PrefsSettingsRepository
import com.quranrecite.app.settings.SettingsRepository
import com.quranrecite.app.speech.NoOpSpeechEngine
import com.quranrecite.app.speech.SpeechEngine

/** Manual DI. Later agents swap the stub implementations here and nowhere else. */
class AppContainer(context: Context) {
    val quranRepository: QuranRepository = StubQuranRepository()      // Agent 2
    val speechEngine: SpeechEngine = NoOpSpeechEngine()               // Agent 3
    val recitationMatcher: RecitationMatcher = NoOpRecitationMatcher() // Agent 4
    val settingsRepository: SettingsRepository = PrefsSettingsRepository(context.applicationContext)
    val sessionStore: SessionStore = InMemorySessionStore()
}
