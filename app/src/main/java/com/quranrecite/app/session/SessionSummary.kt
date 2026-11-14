package com.quranrecite.app.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionSummary(
    val startedAtMillis: Long,
    val durationMillis: Long,
    val pageNumber: Int,
    val revealedWords: Int,
    val mistakes: Int,
    /** Global word ids the user got wrong, for review. */
    val mistakeWordIds: List<Int> = emptyList(),
)

interface SessionStore {
    /** Newest first. */
    val sessions: StateFlow<List<SessionSummary>>
    suspend fun save(summary: SessionSummary)
}

/** In-memory placeholder; Agent 6 may persist to a local file/SQLite. */
class InMemorySessionStore : SessionStore {
    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    override val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()
    override suspend fun save(summary: SessionSummary) { _sessions.value = listOf(summary) + _sessions.value }
}
