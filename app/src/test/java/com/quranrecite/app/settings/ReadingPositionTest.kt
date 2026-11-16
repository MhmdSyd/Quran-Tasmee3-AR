package com.quranrecite.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPositionTest {
    @Test fun validPagesAreKept() {
        assertEquals(1, ReadingPositionRules.sanitize(1))
        assertEquals(128, ReadingPositionRules.sanitize(128))
        assertEquals(604, ReadingPositionRules.sanitize(604))
    }

    @Test fun missingOrOutOfRangeFallsBackToPageOne() {
        assertEquals(1, ReadingPositionRules.sanitize(null))
        assertEquals(1, ReadingPositionRules.sanitize(0))
        assertEquals(1, ReadingPositionRules.sanitize(-5))
        assertEquals(1, ReadingPositionRules.sanitize(605))
    }
}
