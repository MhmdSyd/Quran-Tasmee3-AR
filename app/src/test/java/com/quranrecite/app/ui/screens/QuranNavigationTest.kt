package com.quranrecite.app.ui.screens

import com.quranrecite.app.domain.JuzStarts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranNavigationTest {
    @Test fun pageNumberAcceptsBoundsAndArabicDigits() {
        assertEquals(1, parseNumberInRange("1", 604))
        assertEquals(604, parseNumberInRange("604", 604))
        assertEquals(255, parseNumberInRange("\u0662\u0665\u0665", 604))
        assertEquals(30, parseNumberInRange(" 30 ", 604))
    }

    @Test fun invalidPageNumbersAreRejected() {
        for (bad in listOf("", " ", "0", "605", "-1", "12a", "1.5", "99999", "abc")) assertNull(bad, parseNumberInRange(bad, 604))
    }

    @Test fun juzTableHasThirtyOrderedAyahs() {
        assertEquals(30, JuzStarts.FIRST_AYAH.size)
        assertEquals(1 to 1, JuzStarts.FIRST_AYAH.first())
        assertEquals(78 to 1, JuzStarts.FIRST_AYAH.last())
        val keys = JuzStarts.FIRST_AYAH.map { it.first * 1000 + it.second }
        assertTrue(keys.zipWithNext().all { (a, b) -> a < b })
    }
}
