package com.quranrecite.app.speech.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordParserAndAlphabetTest {
    private val sep = "\u2581"

    private fun result(raw: String, times: List<Float>? = null): TranscriptionResult {
        val tokens = raw.map { it.toString() }
        return TranscriptionResult(raw, tokens, (times ?: tokens.indices.map { it * 0.04f }).toFloatArray())
    }

    @Test fun parsesWordsWithTimesAndOffset() {
        val raw = "${sep}ابت${sep}ثج"
        val w = WordParser.parse(result(raw), offsetMs = 1000, audioDurationMs = 1000)
        assertEquals(listOf("ابت", "ثج"), w.map { it.text })
        // tokens: ▁(0) ا(40) ب(80) ت(120) ▁(160) ث(200) ج(240) ms + 1000
        assertEquals(1040L, w[0].startMs); assertEquals(1120L + 40, w[0].endMs)
        assertEquals(1200L, w[1].startMs); assertEquals(1240L + 40, w[1].endMs)
    }

    @Test fun emptyOrBlankOutputGivesNoWords() {
        assertTrue(WordParser.parse(TranscriptionResult(""), 0, 1000).isEmpty())
        assertTrue(WordParser.parse(TranscriptionResult(sep + sep), 0, 1000).isEmpty())
    }

    @Test fun missingTimestampsFallBackToEvenSpacingButKeepTheText() {
        val w = WordParser.parse(TranscriptionResult("${sep}الله${sep}احد"), offsetMs = 500, audioDurationMs = 2000)
        assertEquals(listOf("الله", "احد"), w.map { it.text })
        assertEquals(500L, w[0].startMs); assertEquals(1500L, w[0].endMs); assertEquals(1500L, w[1].startMs)
    }

    @Test fun inconsistentTimestampArraysFallBack() {
        val r = TranscriptionResult("${sep}ابت", listOf(sep, "ا", "ب", "ت"), floatArrayOf(0f, 0.04f))   // size mismatch
        assertEquals(listOf("ابت"), WordParser.parse(r, 0, 400).map { it.text })
    }

    @Test fun partialTrackerReportsAgreedPrefixWithoutTheLastWord() {
        val t = PartialTracker()
        assertEquals(0, t.update(0, listOf("a", "b")))
        assertEquals(2, t.update(0, listOf("a", "b", "c")))          // a,b agreed; c is the newest last word (excluded by the cap)
        assertEquals(3, t.update(0, listOf("a", "b", "c", "d")))
        assertEquals(1, t.update(0, listOf("a", "x", "c", "d")))     // b changed -> only "a" agrees
        assertEquals(0, t.update(1, listOf("a", "x", "c", "d")))     // new segment: no history
        t.reset()
        assertEquals(0, t.update(1, listOf("q")))
    }

    @Test fun foldMatchesTheModelAuthorsExamples() {
        // from the model card: السموت, الكتب, ءامنوا
        assertEquals("السموت", ModelAlphabet.foldWord("\u0671\u0644\u0633\u0651\u064E\u0645\u064E\u0670\u0648\u064E\u0670\u062A\u0650"))
        assertEquals("ءامنوا", ModelAlphabet.foldWord("\u0621\u064E\u0627\u0645\u064E\u0646\u064F\u0648\u0627\u06DF"))
        assertEquals("الكتب", ModelAlphabet.foldWord("\u0671\u0644\u0652\u0643\u0650\u062A\u064E\u0670\u0628\u0650"))
        // folds: ة->ه, ى->ي, أ/إ/آ->ا, ؤ->و, ئ->ي
        assertEquals("رحمه", ModelAlphabet.foldWord("\u0631\u062D\u0645\u0629"))
        assertEquals("موسي", ModelAlphabet.foldWord("\u0645\u0648\u0633\u0649"))
        assertEquals("ايو", ModelAlphabet.foldWord("\u0625\u064A\u0624"))
        assertEquals("", ModelAlphabet.foldWord("\u06DE\u0661\u0662"))   // ۞ and Arabic-Indic digits vanish
    }

    @Test fun foldWordsSplitsOnSeparatorAndSpaces() {
        assertEquals(listOf("الله", "احد"), ModelAlphabet.foldWords("${sep}الله${sep}احد"))
        assertEquals("قل هو", ModelAlphabet.foldText("قُلۡ\u00A0هُوَ"))
    }

    @Test fun lettersEqualTheVocabularyOfTokensTxt() {
        val f = listOf("src/main/assets/asr/quran-asr-small/tokens.txt", "app/src/main/assets/asr/quran-asr-small/tokens.txt")
            .map(::File).first { it.exists() }
        val vocab = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { it.substringBeforeLast(' ') }
        assertEquals("<blk>", vocab.first())
        assertEquals(sep, vocab.last())
        assertEquals(ModelAlphabet.LETTERS, vocab.drop(1).dropLast(1).map { it.single() }.toSet())
        assertEquals(31, vocab.size)
    }
}
