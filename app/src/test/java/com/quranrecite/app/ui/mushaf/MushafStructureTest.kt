package com.quranrecite.app.ui.mushaf

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Page structure the renderer relies on (mirrors tools/check_mushaf_structure.py). */
class MushafStructureTest {
    private class L(val page: Int, val line: Int, val type: String, val centered: Boolean, val surah: Int)

    companion object {
        private lateinit var db: Connection
        private lateinit var lines: List<L>

        @BeforeClass @JvmStatic fun open() {
            db = DriverManager.getConnection("jdbc:sqlite:" + File("src/main/assets/quran-index.db").absolutePath)
            val rs = db.createStatement().executeQuery(
                "select page,line,type,centered,coalesce(surah,0) from lines order by page,line")
            val out = ArrayList<L>()
            while (rs.next()) out.add(L(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4) == 1, rs.getInt(5)))
            lines = out
        }

        @AfterClass @JvmStatic fun close() = db.close()
    }

    @Test fun everyPageHasItsLinesInOrder() {
        for (p in 1..604) {
            val ls = lines.filter { it.page == p }
            assertEquals("page $p", if (p <= 2) 8 else 15, ls.size)
            assertEquals((1..ls.size).toList(), ls.map { it.line })
        }
    }

    @Test fun headersAndBasmallahCounts() {
        assertEquals(114, lines.count { it.type == "surah_name" })
        assertEquals(112, lines.count { it.type == "basmallah" })
        assertTrue(lines.filter { it.type != "ayah" }.all { it.centered })
    }

    @Test fun basmallahFollowsItsHeaderExceptSurahs1And9() {
        val ordered = lines // already (page,line) ordered, so "next" crosses page boundaries
        for ((i, l) in ordered.withIndex()) {
            if (l.type != "surah_name") continue
            val next = ordered.getOrNull(i + 1)
            val hasBasmallah = next != null && next.type == "basmallah"
            assertEquals("surah ${l.surah}", l.surah != 1 && l.surah != 9, hasBasmallah)
        }
    }

    @Test fun representativePagesHaveMultipleHeaders() {
        for (p in intArrayOf(586, 601, 602, 603, 604)) {
            assertTrue("page $p", lines.count { it.page == p && it.type == "surah_name" } >= 2)
        }
    }
}
