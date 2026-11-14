package com.quranrecite.app.data

import com.quranrecite.app.data.WordMapper.Adjustment
import com.quranrecite.app.data.WordMapper.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordMapperTest {
    private val fatiha1 = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ ١"

    @Test fun tokenizeSplitsOnSpaceAndNbspAndDropsOrnament() {
        val t = WordMapper.tokenize("۞ وَقَالَ ٱلَّذِينَ  ۞ كَفَرُواْ ٢")
        assertEquals(listOf("وَقَالَ", "ٱلَّذِينَ", "كَفَرُواْ", "٢"), t)
    }

    @Test fun plainVerseBecomesFiveWordsWithMarkerLast() {
        val w = WordMapper.buildWords(1, 1, 1, fatiha1, null)
        assertEquals((1..5).toList(), w.map { it.id })
        assertEquals((1..5).toList(), w.map { it.indexInAyah })
        assertTrue(w.last().isAyahMarker); assertEquals("١", w.last().text)
        assertFalse(w.dropLast(1).any { it.isAyahMarker })
        assertEquals("بِسۡمِ", w[0].text) // verbatim, diacritics intact
    }

    @Test fun splitGivesTokenTwoIdsAndKeepsTextVerbatim() {
        val w = WordMapper.buildWords(1, 1, 10, fatiha1, Adjustment(Kind.SPLIT, 2))
        assertEquals(listOf(10, 11, 13, 14, 15), w.map { it.id })
        assertEquals(11, w[1].id); assertEquals(12, w[1].idEnd); assertEquals("ٱللَّهِ", w[1].text)
        assertEquals(15, w.last().idEnd)
    }

    @Test fun mergeJoinsTwoTokensWithOneSpace() {
        val w = WordMapper.buildWords(1, 1, 1, fatiha1, Adjustment(Kind.MERGE, 2))
        assertEquals(4, w.size)
        assertEquals("ٱللَّهِ ٱلرَّحۡمَٰنِ", w[1].text)
        assertEquals(listOf(1, 2, 3, 4), w.map { it.id })
    }
}
