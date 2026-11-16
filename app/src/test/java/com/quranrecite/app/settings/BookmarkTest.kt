package com.quranrecite.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkTest {
    @Test fun validBookmarks() {
        assertTrue(Bookmark(1, 1).isValid)
        assertTrue(Bookmark(6, 6).isValid)
        assertTrue(Bookmark(114, 6).isValid)
    }

    @Test fun invalidBookmarksAreRejected() {
        assertFalse(Bookmark(0, 1).isValid)
        assertFalse(Bookmark(115, 1).isValid)
        assertFalse(Bookmark(2, 0).isValid)
        assertFalse(Bookmark(-1, -1).isValid)
    }
}
