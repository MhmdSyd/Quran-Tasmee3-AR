package com.quranrecite.app.di

import android.content.Context
import com.quranrecite.app.data.QuranRepository
import com.quranrecite.app.data.SqliteQuranRepository
import com.quranrecite.app.ui.mushaf.MushafDataSource
import com.quranrecite.app.recitation.NoOpRecitationMatcher
import com.quranrecite.app.recitation.RecitationMatcher
import com.quranrecite.app.session.InMemorySessionStore
import com.quranrecite.app.session.SessionStore
import com.quranrecite.app.settings.PrefsSettingsRepository
import com.quranrecite.app.settings.SettingsRepository
import com.quranrecite.app.speech.asr.SherpaSpeechEngine
import com.quranrecite.app.speech.SpeechEngine

/** Manual DI. Later agents swap the stub implementations here and nowhere else. */
class AppContainer(context: Context) {
    val quranRepository: QuranRepository = SqliteQuranRepository(context.applicationContext) // Agent 2 (done)
    val mushafData = MushafDataSource(quranRepository)               // Agent 3 (page cache for the mushaf UI)
    /** Agent 4: offline ASR. Construction is cheap; the model loads on prepare()/start(). Also exposes metrics + benchmark. */
    val asrEngine = SherpaSpeechEngine(context.applicationContext)
    val speechEngine: SpeechEngine = asrEngine
    val recitationMatcher: RecitationMatcher = NoOpRecitationMatcher() // Agent 4
    val settingsRepository: SettingsRepository = PrefsSettingsRepository(context.applicationContext)
    val sessionStore: SessionStore = InMemorySessionStore()
}
