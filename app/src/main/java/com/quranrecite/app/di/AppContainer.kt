package com.quranrecite.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.quranrecite.app.data.QuranRepository
import com.quranrecite.app.data.SqliteQuranRepository
import com.quranrecite.app.ui.mushaf.MushafDataSource
import com.quranrecite.app.recitation.TrackingRecitationMatcher
import com.quranrecite.app.recitation.RecitationMatcher
import com.quranrecite.app.session.ActiveTimer
import com.quranrecite.app.session.InMemorySessionStore
import com.quranrecite.app.session.SessionStore
import com.quranrecite.app.settings.PrefsSettingsRepository
import com.quranrecite.app.settings.SettingsRepository
import com.quranrecite.app.speech.asr.SherpaSpeechEngine
import com.quranrecite.app.speech.SpeechEngine

/**
 * The recitation in progress. Lives in the container (not in a composable) so that opening Settings from the
 * recitation screen, or any other recomposition of the screen, resumes it instead of restarting from word 1.
 */
class RecitationSession(val page: Int, val startedAt: Long, val timer: ActiveTimer)

/** Manual DI. Later agents swap the stub implementations here and nowhere else. */
class AppContainer(context: Context) {
    val quranRepository: QuranRepository = SqliteQuranRepository(context.applicationContext) // Agent 2 (done)
    val mushafData = MushafDataSource(quranRepository)               // Agent 3 (page cache for the mushaf UI)
    /** Agent 4: offline ASR. Construction is cheap; the model loads on prepare()/start(). Also exposes metrics + benchmark. */
    val asrEngine = SherpaSpeechEngine(context.applicationContext)
    val speechEngine: SpeechEngine = asrEngine
    val settingsRepository: SettingsRepository = PrefsSettingsRepository(context.applicationContext)
    /** Agent 5: word matching + position tracking. Strictness is read from the settings on every event. */
    val recitationMatcher: RecitationMatcher = TrackingRecitationMatcher { settingsRepository.settings.value.strictness }
    /** Survives screen changes; used to stop the microphone when the recitation screen is left. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sessionStore: SessionStore = InMemorySessionStore()

    /** Non-null while a recitation is in progress; cleared when it is finished or abandoned. */
    @Volatile var activeSession: RecitationSession? = null
        private set

    fun beginSession(page: Int): RecitationSession =
        RecitationSession(page, System.currentTimeMillis(), ActiveTimer()).also { activeSession = it }

    fun endSession() { activeSession = null }
}
