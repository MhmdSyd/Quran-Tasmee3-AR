package com.quranrecite.app.recitation

import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.recitation.ArabicMatch.Match
import com.quranrecite.app.recitation.Step.Kind
import com.quranrecite.app.settings.Strictness
import com.quranrecite.app.speech.SpeechEvent
import com.quranrecite.app.speech.asr.ModelAlphabet

/**
 * The position tracker. One instance follows one page. Not thread-safe (see [TrackingRecitationMatcher]).
 *
 * Principles (details in docs/handoffs/AGENT_5_HANDOFF.md):
 *  1. The canonical words come from the page and are never altered; recognised text is only compared, never shown.
 *  2. The cursor (next expected word) only moves on evidence: a word is accepted when it matches closely, or when the
 *     words around it do. A far jump needs a multi-word anchor. Weak, ambiguous or isolated hits do not move it.
 *  3. Every hypothesis of a speech segment is re-aligned from the cursor the segment started at, so re-decoding the
 *     same audio again and again (Partials, then the Final) can never reveal a word twice. Reveals only grow.
 *  4. Nothing here judges pronunciation. Words are Revealed, Uncertain or Skipped; "for review" is neutral.
 */
class RecitationTracker(private val strictness: () -> Strictness = { Strictness.Normal }) {

    private var pageNumber = 0
    private var words: List<QuranWord> = emptyList()
    private var folded: List<String> = emptyList()
    private var state: Array<WordState> = emptyArray()
    private var reason: Array<ReviewReason?> = emptyArray()
    private var resolvedSeg: IntArray = IntArray(0)

    private var scopeStart = 0
    private var cursor = 0
    private var finished = false
    private var lastRecognized = -1

    private var basmalaFolded: List<String> = emptyList()
    private var basmalaPositions: Set<Int> = emptySet()

    // ---- speech bookkeeping
    private var curSeg = Int.MIN_VALUE
    private var segBase = 0
    private var segPreamble = false
    private var lastFinalSeg = Int.MIN_VALUE
    private var lastFinalEndMs = 0L
    /** Heard words whose audio lies before this time were already consumed (overlapping chunks, manual moves). */
    private var dropBeforeMs = 0L
    private var lastAudioEndMs = 0L
    private var sawListening = false

    private var feedback = RecitationFeedback.None
    private var feedbackSeq = 0
    private var repetitions = 0
    private var retries = 0

    private val n get() = words.size

    /** Progress of pages left during this session (arrows, show ayah, continuous recitation), restored when coming back. */
    private class PageSnapshot(
        val state: Array<WordState>, val reason: Array<ReviewReason?>, val resolvedSeg: IntArray,
        val scopeStart: Int, val cursor: Int, val finished: Boolean, val lastRecognized: Int,
        val segBase: Int, val segPreamble: Boolean, val repetitions: Int, val retries: Int,
    )
    private val savedPages = HashMap<Int, PageSnapshot>()

    private companion object {
        /** At the very start, a jump over this many words means "the reciter started elsewhere", not "skipped". */
        const val RELOCATE_DISTANCE = 5

        /** Jumps over at least this many words are checked against "the reciter just said this again". */
        const val REPEAT_GUARD_DISTANCE = 3
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    fun begin(page: QuranPage, startWordId: Int?, basmallah: List<String>) {
        reset()
        pageNumber = page.number
        words = page.recitableWords
        folded = words.map { ArabicMatch.fold(it.text) }
        state = Array(n) { WordState.Hidden }
        reason = arrayOfNulls(n)
        resolvedSeg = IntArray(n) { Int.MIN_VALUE }
        basmalaFolded = basmallah.map { ArabicMatch.fold(it) }.filter { it.isNotEmpty() }

        val positions = HashSet<Int>()
        var seen = 0
        for (line in page.lines) when (line) {
            is PageLine.Ayahs -> seen += line.words.count { !it.isAyahMarker }
            is PageLine.Basmallah -> positions += seen
            else -> Unit
        }
        basmalaPositions = positions

        val start = if (startWordId == null) 0 else words.indexOfFirst { it.id >= startWordId }.let { if (it < 0) 0 else it }
        scopeStart = start
        cursor = start
        segBase = start
        finished = n == 0
    }

    fun reset() {
        pageNumber = 0; words = emptyList(); folded = emptyList()
        state = emptyArray(); reason = emptyArray(); resolvedSeg = IntArray(0)
        scopeStart = 0; cursor = 0; finished = false; lastRecognized = -1
        basmalaFolded = emptyList(); basmalaPositions = emptySet()
        curSeg = Int.MIN_VALUE; segBase = 0; segPreamble = false
        lastFinalSeg = Int.MIN_VALUE; lastFinalEndMs = 0; dropBeforeMs = 0; lastAudioEndMs = 0; sawListening = false
        feedback = RecitationFeedback.None; feedbackSeq = 0; repetitions = 0; retries = 0
        savedPages.clear()
    }

    // ------------------------------------------------------------------------------------------------ speech input

    private class Heard(val text: String, val startMs: Long, val endMs: Long)

    fun onEvent(e: SpeechEvent) {
        when (e) {
            is SpeechEvent.Listening -> sawListening = true
            is SpeechEvent.Partial -> {
                val src = wordsOf(e.words.map { Triple(it.text, it.startMs, it.endMs) }, e.text)
                handle(e.segmentId, e.audioStartMs, e.audioEndMs, src.take(e.stableWordCount.coerceAtLeast(0)), false)
            }
            is SpeechEvent.Final ->
                handle(e.segmentId, e.audioStartMs, e.audioEndMs, wordsOf(e.words.map { Triple(it.text, it.startMs, it.endMs) }, e.text), true)
            is SpeechEvent.Error -> Unit
        }
    }

    private fun wordsOf(timed: List<Triple<String, Long, Long>>, text: String): List<Heard> =
        if (timed.isNotEmpty()) timed.map { Heard(it.first, it.second, it.third) }
        else text.split(' ', ' ', '▁').filter { it.isNotBlank() }.map { Heard(it, -1, -1) }

    private fun handle(segId: Int, audioStart: Long, audioEnd: Long, heard: List<Heard>, final: Boolean) {
        val lastWordEnd = heard.maxOfOrNull { it.endMs }?.takeIf { it > 0 } ?: audioEnd
        if (n == 0 || finished) return
        lastAudioEndMs = maxOf(lastAudioEndMs, audioEnd)

        // A new engine run restarts segment ids and the audio clock (Listening precedes it). Resume keeps both.
        if (sawListening &&
            (audioStart + 500 < dropBeforeMs || (segId <= lastFinalSeg && audioStart <= lastFinalEndMs))
        ) {
            lastFinalSeg = Int.MIN_VALUE; lastFinalEndMs = 0; curSeg = Int.MIN_VALUE; dropBeforeMs = 0
        }
        sawListening = false

        if (segId <= lastFinalSeg) return                 // duplicate or stale delivery of a closed segment
        if (segId != curSeg) {
            curSeg = segId; segBase = cursor; segPreamble = cursor in basmalaPositions
        }

        // audio already consumed (overlapping chunks): drop by time; text-only events carry no times
        var hyp = heard.filter { it.startMs < 0 || (it.startMs + it.endMs) / 2 >= dropBeforeMs }
            .map { ArabicMatch.fold(it.text) }.filter { it.isNotEmpty() }
        if (segPreamble) hyp = dropPreamble(hyp)

        if (hyp.isNotEmpty()) process(hyp, final)

        if (final) {
            lastFinalSeg = segId
            lastFinalEndMs = audioEnd
            // the audio of this segment is consumed up to its last word (not its silent tail pad)
            dropBeforeMs = maxOf(dropBeforeMs, minOf(audioEnd, lastWordEnd))
        }
    }

    /** Removes a leading basmala (as many words of it as were heard, at least two) from the heard words. */
    private fun dropPreamble(hyp: List<String>): List<String> {
        var c = 0
        while (c < hyp.size && c < basmalaFolded.size &&
            ArabicMatch.classify(hyp[c], basmalaFolded[c], strictness()) == Match.Strong
        ) c++
        return if (c >= minOf(2, basmalaFolded.size) && c > 0) hyp.drop(c) else hyp
    }

    private class Commit {
        var any = false
        var changed = false
        var uncertain = false
        var skip = false
        var rejectedJump = false
        var repetition = false
        /** Heard words that matched no expected word; judged after the cursor has moved (see [onExtraWord]). */
        val extras = ArrayList<String>()
    }

    private fun process(hyp: List<String>, final: Boolean) {
        val s = strictness()
        val base = segBase
        var window = minOf(n - base, hyp.size + 20)
        var r = commit(WordAligner.align(hyp, folded.subList(base, base + window), s), base, hyp, final)
        // nothing near the cursor explains the speech: look at the rest of the page (needs a strong multi-word anchor)
        if (!r.any && hyp.size >= 3 && base + window < n) {
            window = n - base
            val r2 = commit(WordAligner.align(hyp, folded.subList(base, base + window), s), base, hyp, final)
            if (r2.any || r2.changed) r = r2 else r.extras.clear().also { r.extras += r2.extras }
        }

        for (x in r.extras) onExtraWord(x, r, final)
        val fb = when {
            r.uncertain -> RecitationFeedback.Uncertain
            r.skip || r.rejectedJump -> RecitationFeedback.PossibleSkip
            r.repetition -> RecitationFeedback.PossibleRepetition
            final && !r.any && !r.changed && hyp.size >= 2 -> RecitationFeedback.NoMatch
            else -> null
        }
        if (fb != null && (fb != feedback || r.changed)) { feedback = fb; feedbackSeq++ }
        if (cursor >= n) finished = true
    }

    // ------------------------------------------------------------------------------------------------ commit rules

    private fun letters(st: Step, base: Int): Int = when (st.kind) {
        Kind.Merge -> folded[base + st.e].length + folded[base + st.e + 1].length
        else -> folded[base + st.e].length
    }

    /**
     * May the cursor jump over [skipped] words, given the run of consecutive strong matches that follows?
     * Skipping nothing needs no anchor; every further word of distance needs more evidence.
     */
    private fun anchorOk(skipped: Int, strongCount: Int, strongLetters: Int): Boolean = when {
        skipped == 0 -> true
        skipped == 1 -> strongCount >= 2 || (strongCount == 1 && strongLetters >= 3)
        skipped <= 4 -> strongCount >= 2 && strongLetters >= 6
        else -> strongCount >= 3 && strongLetters >= 9
    }

    private fun commit(steps: List<Step>, base: Int, hyp: List<String>, final: Boolean): Commit {
        val r = Commit()
        val pending = ArrayList<Step>()      // weak / missed / skipped words waiting for a strong word after them
        var i = 0
        while (i < steps.size) {
            val st = steps[i]
            when {
                st.kind == Kind.Ins -> if (pending.isEmpty()) r.extras += hyp[st.h]
                st.isStrong -> {
                    var count = 0; var lettersSum = 0; var k = i
                    while (k < steps.size) {
                        val t = steps[k]
                        if (t.kind == Kind.Ins) { k++; continue }
                        if (!t.isStrong) break
                        count++; lettersSum += letters(t, base); k++
                    }
                    val distance = pending.count { it.kind == Kind.Skip || it.isMiss }
                    if (!anchorOk(distance, count, lettersSum)) { r.rejectedJump = true; return r }
                    if (distance >= REPEAT_GUARD_DISTANCE && looksLikeRepeat(hyp, st.h, count)) { r.repetition = true; if (final) repetitions++; return r }
                    if (distance >= RELOCATE_DISTANCE && (scopeStart until n).all { state[it] == WordState.Hidden }) {
                        // the reciter simply started somewhere else on the page: that is the new start, nothing was skipped
                        scopeStart = base + st.e
                        cursor = scopeStart
                    } else {
                        for (p in pending) commitPending(p, base, r)
                    }
                    pending.clear()
                    commitStrong(st, base, r)
                }
                else -> pending += st
            }
            i++
        }
        return r
    }

    /**
     * True when the words that would justify a jump are really the last words the reciter already said, said again
     * (a restart or a repeated phrase). Such speech is ambiguous, so it is reported as a repetition instead of a jump.
     * Words revealed by the current segment are excluded so that re-decoding the same audio never trips this.
     */
    private fun looksLikeRepeat(hyp: List<String>, h0: Int, strongCount: Int): Boolean {
        val m = minOf(strongCount, 3, hyp.size - h0)
        if (m < 2) return false
        val s = strictness()
        for (k in maxOf(scopeStart, cursor - 16)..(cursor - m)) {
            var all = true
            for (t in 0 until m) {
                if (state[k + t] == WordState.Hidden || resolvedSeg[k + t] == curSeg ||
                    ArabicMatch.classify(hyp[h0 + t], folded[k + t], s) != Match.Strong
                ) { all = false; break }
            }
            if (all) return true
        }
        return false
    }

    private fun commitPending(p: Step, base: Int, r: Commit) {
        val idx = base + p.e
        when {
            p.kind == Kind.Skip -> { setIfNew(idx, WordState.Skipped, ReviewReason.PossiblySkipped, r); r.skip = true }
            p.isMiss -> { setIfNew(idx, WordState.Skipped, ReviewReason.NotRecognized, r); r.uncertain = true }
            else -> { setIfNew(idx, WordState.Uncertain, ReviewReason.Uncertain, r); r.uncertain = true; r.any = true }
        }
    }

    private fun commitStrong(st: Step, base: Int, r: Commit) {
        val idx = base + st.e
        setIfNew(idx, WordState.Revealed, null, r)
        if (st.kind == Kind.Merge) setIfNew(idx + 1, WordState.Revealed, null, r)
        r.any = true
    }

    private fun isAutoReason(x: ReviewReason?) =
        x == null || x == ReviewReason.Uncertain || x == ReviewReason.NotRecognized || x == ReviewReason.PossiblySkipped

    /** Applies a resolution to word [idx]: forward only, except that a better reading of the same segment may upgrade it. */
    private fun setIfNew(idx: Int, st: WordState, why: ReviewReason?, r: Commit) {
        if (idx !in 0 until n || idx < scopeStart) return
        if (idx >= cursor) {
            for (g in cursor until idx) if (state[g] == WordState.Hidden) set(g, WordState.Skipped, ReviewReason.PossiblySkipped)
            set(idx, st, why)
            resolvedSeg[idx] = curSeg
            cursor = idx + 1
            if (st != WordState.Skipped) lastRecognized = idx
            r.changed = true
        } else if (st == WordState.Revealed && resolvedSeg[idx] == curSeg && isAutoReason(reason[idx]) &&
            (state[idx] == WordState.Uncertain || state[idx] == WordState.Skipped)
        ) {
            set(idx, WordState.Revealed, null)
            lastRecognized = idx
            r.changed = true
        }
    }

    private fun set(idx: Int, st: WordState, why: ReviewReason?) { state[idx] = st; reason[idx] = why }

    /** A heard word with no counterpart: either a correction of a recent miss, or a repetition, or noise. */
    private fun onExtraWord(h: String, r: Commit, final: Boolean) {
        val s = strictness()
        for (k in cursor - 1 downTo maxOf(scopeStart, cursor - 6)) {
            val st = state[k]
            if ((st == WordState.Skipped || st == WordState.Uncertain) && isAutoReason(reason[k]) &&
                ArabicMatch.classify(h, folded[k], s) == Match.Strong
            ) {
                val wasUncertain = st == WordState.Uncertain
                set(k, WordState.Revealed, if (wasUncertain) null else ReviewReason.SelfCorrected)
                lastRecognized = k
                r.changed = true
                return
            }
        }
        for (k in cursor - 1 downTo maxOf(scopeStart, cursor - 12)) {
            if ((state[k] == WordState.Revealed || state[k] == WordState.Uncertain) &&
                ArabicMatch.classify(h, folded[k], s) == Match.Strong
            ) {
                r.repetition = true
                if (final) repetitions++
                return
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ manual correction

    private fun indexOfWord(id: Int) = words.indexOfFirst { id in it.id..it.idEnd }

    private fun afterManualChange() {
        segBase = cursor
        segPreamble = cursor in basmalaPositions
        dropBeforeMs = maxOf(dropBeforeMs, lastAudioEndMs)
        finished = cursor >= n
        feedback = RecitationFeedback.None
    }

    fun moveTo(wordId: Int) {
        val idx = indexOfWord(wordId)
        if (idx < 0) return
        when {
            idx > cursor -> {
                if (state.all { it == WordState.Hidden }) { scopeStart = idx }
                else for (k in cursor until idx) if (state[k] == WordState.Hidden) set(k, WordState.Skipped, ReviewReason.ManuallySkipped)
                cursor = idx
            }
            idx < cursor -> {
                retries++
                for (k in idx until n) { set(k, WordState.Hidden, null); resolvedSeg[k] = Int.MIN_VALUE }
                if (idx < scopeStart) scopeStart = idx
                cursor = idx
                lastRecognized = -1
            }
        }
        afterManualChange()
    }

    fun markRecited(wordId: Int) {
        val idx = indexOfWord(wordId)
        if (idx < scopeStart || idx > cursor || idx >= n) return
        if (state[idx] == WordState.Revealed && reason[idx] == null) return
        set(idx, WordState.Revealed, ReviewReason.Manual)
        lastRecognized = idx
        if (idx == cursor) cursor++
        afterManualChange()
    }

    fun hint() {
        if (finished || cursor >= n) return
        set(cursor, WordState.Revealed, ReviewReason.Hinted)
        lastRecognized = cursor
        cursor++
        afterManualChange()
    }

    /**
     * "Show ayah": reveals every word of the ayah the cursor is in (the ayah of the next expected word) and puts the
     * cursor on the first word after it, so the next call shows the next ayah. Words already recognised keep their
     * state; hidden or skipped ones become [WordState.Revealed] with [ReviewReason.Manual]. Only words of this page are
     * touched. Returns [ShowAyahResult.PageEnd] when no word of this page is left (the caller moves to the next page).
     */
    fun showAyah(): ShowAyahResult {
        if (n == 0 || cursor >= n) return ShowAyahResult.PageEnd
        val w = words[cursor]
        fun same(k: Int) = words[k].surah == w.surah && words[k].ayah == w.ayah
        var first = cursor
        while (first > 0 && same(first - 1)) first--
        var last = cursor
        while (last + 1 < n && same(last + 1)) last++
        for (k in first..last) {
            if (state[k] == WordState.Hidden || state[k] == WordState.Skipped) set(k, WordState.Revealed, ReviewReason.Manual)
        }
        if (first < scopeStart) scopeStart = first
        lastRecognized = last
        cursor = last + 1
        afterManualChange()
        return ShowAyahResult.Shown
    }

    /**
     * Continue on another page without losing the speech bookkeeping (segment ids, consumed audio), so audio that was
     * already handled on the previous page is not matched again against the new one.
     */
    fun continueOnPage(page: QuranPage, basmallah: List<String>) {
        val seg = curSeg; val finalSeg = lastFinalSeg; val finalEnd = lastFinalEndMs
        val drop = dropBeforeMs; val audioEnd = lastAudioEndMs
        // remember the page we leave (all pages left in this session), then start the new one
        if (pageNumber > 0 && n > 0) {
            savedPages[pageNumber] = PageSnapshot(
                state.copyOf(), reason.copyOf(), resolvedSeg.copyOf(), scopeStart, cursor, finished, lastRecognized,
                segBase, segPreamble, repetitions, retries,
            )
        }
        val keep = HashMap(savedPages)
        begin(page, null, basmallah)
        savedPages.putAll(keep)
        // coming back to a page already recited in this session: its content stays revealed
        savedPages[page.number]?.takeIf { it.state.size == n }?.let { s ->
            for (k in 0 until n) { state[k] = s.state[k]; reason[k] = s.reason[k]; resolvedSeg[k] = s.resolvedSeg[k] }
            scopeStart = s.scopeStart; cursor = s.cursor; finished = s.finished; lastRecognized = s.lastRecognized
            segBase = s.segBase; segPreamble = s.segPreamble; repetitions = s.repetitions; retries = s.retries
        }
        curSeg = seg; lastFinalSeg = finalSeg; lastFinalEndMs = finalEnd
        lastAudioEndMs = audioEnd; dropBeforeMs = maxOf(drop, audioEnd)
    }

    // ------------------------------------------------------------------------------------------------ output

    fun progress(): RecitationProgress {
        val map = HashMap<Int, WordState>()
        var revealed = 0; var unc = 0; var skipped = 0; var hints = 0
        for (k in scopeStart until n) {
            val st = state[k]
            if (st != WordState.Hidden) map[words[k].id] = st
            when (st) {
                WordState.Revealed -> revealed++
                WordState.Uncertain -> { revealed++; unc++ }
                WordState.Skipped -> skipped++
                WordState.Hidden -> Unit
            }
            if (reason[k] == ReviewReason.Hinted) hints++
        }
        return RecitationProgress(
            pageNumber = pageNumber,
            wordStates = map,
            currentWordId = if (finished || cursor >= n) null else words[cursor].id,
            lastRecognizedId = if (lastRecognized in 0 until n) words[lastRecognized].id else null,
            totalWords = n - scopeStart,
            passedWords = revealed + skipped,
            revealedCount = revealed, uncertainCount = unc, skippedCount = skipped, hintCount = hints,
            feedback = feedback, feedbackSeq = feedbackSeq, finished = finished,
        )
    }

    fun report(): RecitationReport {
        val items = ArrayList<ReviewItem>()
        var confident = 0; var passed = 0
        val reached = LinkedHashSet<Pair<Int, Int>>()
        val open = HashSet<Pair<Int, Int>>()
        val seen = LinkedHashSet<Pair<Int, Int>>()
        for (k in scopeStart until n) {
            val key = words[k].surah to words[k].ayah
            seen += key
            if (state[k] == WordState.Hidden) { open += key; continue }
            passed++
            reached += key
            val why = reason[k]
            if (why == null && state[k] == WordState.Revealed) confident++ else if (why != null) items += ReviewItem(words[k], why)
        }
        return RecitationReport(
            pageNumber = pageNumber, totalWords = n - scopeStart, confidentWords = confident, passedWords = passed,
            reviewItems = items, ayahsReached = reached.toList(), ayahsCompleted = seen.filter { it !in open },
            repetitions = repetitions, retries = retries, finished = finished,
        )
    }
}
