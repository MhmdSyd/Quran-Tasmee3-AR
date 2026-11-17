package com.quranrecite.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousSettingTest {
    @Test fun offByDefaultSoTheResultsAreShownAfterEveryPage() {
        assertFalse(RecitationSettings().continuousRecitation)
    }

    @Test fun canBeTurnedOnWithoutChangingOtherSettings() {
        val on = RecitationSettings().copy(continuousRecitation = true)
        assertTrue(on.continuousRecitation)
        assertEquals(RecitationSettings().strictness, on.strictness)
        assertEquals(RecitationSettings().showMistakeImmediately, on.showMistakeImmediately)
    }
}
