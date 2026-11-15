package com.quranrecite.app.recitation

import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.settings.Strictness
import com.quranrecite.app.speech.SpeechEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the page shows for one recitable word. Deliberately NO "mistake" state: the matcher only sees text from an
 * approximate recogniser, so it can say "recognised", "not sure" or "possibly skipped", never "mispronounced".
 */
enum class WordState {
    /** Not recited yet (default for words without an entry in [RecitationProgress.wordStates]). */
    Hidden,

    /** Recognised with enough evidence; the canonical word is shown. */
    Revealed,

    /** Position confirmed by the words around it, but the word itself was only a weak acoustic match. Shown dimmed. */
    Uncertain,

    /** The cursor passed this word without it being recognised (skipped, or not recognised). Stays hidden, marked. */
    Skipped,
}

/** Why a word appears in the "for review" list of the summary. Neutral wording: none of these is a Tajweed verdict. */
enum class ReviewReason {
    /** A different word (or nothing clear) was heard where this one was expected, but later words confirmed the position. */
    NotRecognized,

    /** The reciter seems to have gone on without it. */
    PossiblySkipped,

    /** Weak acoustic match, accepted only because the neighbouring words were clear. */
    Uncertain,

    /** The user asked for the hint. */
    Hinted,

    /** The user revealed it by hand. */
    Manual,

    /** It had been missed, then the user said it again and it was recognised. */
    SelfCorrected,

    /** The user moved the position forward by hand over this word. */
    ManuallySkipped,
}

/** Short, unobtrusive status for the UI (cleared by the UI after a moment). */
enum class RecitationFeedback { None, Uncertain, PossibleSkip, PossibleRepetition, NoMatch }

data class RecitationProgress(
    val pageNumber: Int = 0,
    /** Keyed by [QuranWord.id]. Missing key = [WordState.Hidden]. */
    val wordStates: Map<Int, WordState> = emptyMap(),
    /** The next expected word, null when idle or finished. */
    val currentWordId: Int? = null,
    /** The word recognised most recently (for the "just recognised" highlight). */
    val lastRecognizedId: Int? = null,
    /** Recitable words from the starting position to the end of the page. */
    val totalWords: Int = 0,
    /** Words the cursor has passed (revealed + uncertain + skipped). */
    val passedWords: Int = 0,
    /** Words whose text is shown ([WordState.Revealed] + [WordState.Uncertain]). */
    val revealedCount: Int = 0,
    val uncertainCount: Int = 0,
    val skippedCount: Int = 0,
    val hintCount: Int = 0,
    val feedback: RecitationFeedback = RecitationFeedback.None,
    /** Increases whenever [feedback] should be (re)shown, so the UI can restart its timer. */
    val feedbackSeq: Int = 0,
    val finished: Boolean = false,
)

data class ReviewItem(val word: QuranWord, val reason: ReviewReason)

/** Everything the session summary needs, computed from the tracker. Words are the canonical [QuranWord]s. */
data class RecitationReport(
    val pageNumber: Int,
    val totalWords: Int,
    /** Revealed without any review reason. */
    val confidentWords: Int,
    val passedWords: Int,
    val reviewItems: List<ReviewItem>,
    /** "surah:ayah" keys with at least one word passed. */
    val ayahsReached: List<Pair<Int, Int>>,
    /** Ayahs whose words on this page were all passed. */
    val ayahsCompleted: List<Pair<Int, Int>>,
    val repetitions: Int,
    val retries: Int,
    val finished: Boolean,
)

/**
 * Matches recognised speech against the expected Quran words of one page and drives reveal state.
 * Pure Kotlin, no Android dependencies. See docs/handoffs/AGENT_5_HANDOFF.md for the matching rules.
 */
interface RecitationMatcher {
    val progress: StateFlow<RecitationProgress>

    /**
     * Start matching on [page]; [startWordId] null = first word of the page.
     * [basmallah] = the canonical words of 1:1 (the page shows them on its Basmallah lines, they are not part of
     * [QuranPage.recitableWords]); if the reciter says them before a surah's first ayah they are ignored.
     */
    fun begin(page: QuranPage, startWordId: Int? = null, basmallah: List<String> = emptyList())

    fun onSpeech(event: SpeechEvent)

    /** Manual correction: "I am here". Forward = words in between are marked skipped; backward = retry from there. */
    fun moveTo(wordId: Int)

    /** Manual correction: reveal one already-passed (or the expected) word by hand. */
    fun markRecited(wordId: Int)

    /** Reveal the expected word (the "hint" button) and move on. */
    fun hint()

    fun report(): RecitationReport

    fun reset()
}

class NoOpRecitationMatcher : RecitationMatcher {
    private val _progress = MutableStateFlow(RecitationProgress())
    override val progress: StateFlow<RecitationProgress> = _progress
    override fun begin(page: QuranPage, startWordId: Int?, basmallah: List<String>) {
        _progress.value = RecitationProgress(pageNumber = page.number)
    }
    override fun onSpeech(event: SpeechEvent) {}
    override fun moveTo(wordId: Int) {}
    override fun markRecited(wordId: Int) {}
    override fun hint() {}
    override fun report() = RecitationReport(_progress.value.pageNumber, 0, 0, 0, emptyList(), emptyList(), emptyList(), 0, 0, false)
    override fun reset() { _progress.value = RecitationProgress() }
}

/** Thread-safe [RecitationMatcher] over the pure [RecitationTracker]. */
class TrackingRecitationMatcher(strictness: () -> Strictness = { Strictness.Normal }) : RecitationMatcher {
    private val tracker = RecitationTracker(strictness)
    private val _progress = MutableStateFlow(RecitationProgress())
    override val progress: StateFlow<RecitationProgress> = _progress

    @Synchronized override fun begin(page: QuranPage, startWordId: Int?, basmallah: List<String>) {
        tracker.begin(page, startWordId, basmallah); publish()
    }
    @Synchronized override fun onSpeech(event: SpeechEvent) { tracker.onEvent(event); publish() }
    @Synchronized override fun moveTo(wordId: Int) { tracker.moveTo(wordId); publish() }
    @Synchronized override fun markRecited(wordId: Int) { tracker.markRecited(wordId); publish() }
    @Synchronized override fun hint() { tracker.hint(); publish() }
    @Synchronized override fun report(): RecitationReport = tracker.report()
    @Synchronized override fun reset() { tracker.reset(); publish() }

    private fun publish() { _progress.value = tracker.progress() }
}
