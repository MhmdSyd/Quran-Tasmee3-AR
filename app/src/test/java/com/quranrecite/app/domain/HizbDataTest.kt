package com.quranrecite.app.domain

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Pure logic + the real hizb data shipped in assets/qpc-hafs.db (JVM, via sqlite-jdbc). */
class HizbDataTest {
    companion object {
        private lateinit var db: Connection
        private lateinit var starts: List<Pair<Int, Int>>
        private lateinit var quarters: HizbQuarters

        @BeforeClass @JvmStatic fun setUp() {
            db = DriverManager.getConnection("jdbc:sqlite:" + File("src/main/assets/qpc-hafs.db").absolutePath)
            starts = db.createStatement().executeQuery("SELECT start_verse_key FROM hizb_quarters ORDER BY rub_el_hizb").use { r ->
                buildList { while (r.next()) r.getString(1).split(':').let { add(it[0].toInt() to it[1].toInt()) } }
            }
            quarters = HizbQuarters.from(starts) ?: error("shipped hizb data invalid: ${HizbQuarters.problem(starts)}")
        }
        @AfterClass @JvmStatic fun tearDown() = db.close()
    }

    @Test fun shippedDataIsAValidSetOf240Quarters() {
        assertEquals(240, starts.size)
        assertNull(HizbQuarters.problem(starts))
    }

    @Test fun everyTextSignIsAQuarterStartAndOnlySurahOpeningsHaveNone() {
        val signed = db.createStatement().executeQuery("SELECT surah, ayah FROM verses WHERE text LIKE '%۞%'").use { r ->
            buildSet { while (r.next()) add(r.getInt(1) to r.getInt(2)) }
        }
        assertEquals(199, signed.size)
        assertTrue(starts.toSet().containsAll(signed))
        assertTrue((starts.toSet() - signed).all { it.second == 1 })
    }

    @Test fun labelsOfKnownAyahs() {
        assertEquals(HizbLabel(1, 0, 1), quarters.labelAt(1, 1))        // start of hizb 1
        assertEquals(HizbLabel(1, 1, 2), quarters.labelAt(2, 26))       // first ۞ of the Quran: a quarter
        assertEquals(HizbLabel(1, 3, 4), quarters.labelAt(2, 74))       // last ayah before hizb 2
        assertEquals(HizbLabel(2, 0, 5), quarters.labelAt(2, 75))       // start of hizb 2
        assertEquals(HizbLabel(27, 1, 106), quarters.labelAt(15, 49))   // sign in the text, table corrected from 15:50
        assertEquals(HizbLabel(60, 3, 240), quarters.labelAt(100, 9))
        assertEquals(HizbLabel(60, 3, 240), quarters.labelAt(114, 6))   // last ayah
    }

    @Test fun juzOfLabel() {
        assertEquals(1, quarters.labelAt(1, 1)!!.juz)
        assertEquals(1, quarters.labelAt(2, 141)!!.juz)
        assertEquals(2, quarters.labelAt(2, 142)!!.juz)
        assertEquals(30, quarters.labelAt(114, 6)!!.juz)
    }

    @Test fun juzStartsAreHizbStarts() {
        for ((j, juz) in JuzStarts.FIRST_AYAH.withIndex()) {
            val label = quarters.labelAt(juz.first, juz.second)!!
            assertEquals(0, label.part)
            assertEquals(2 * j + 1, label.hizb)
        }
    }

    @Test fun invalidListsAreRejected() {
        assertNotNull(HizbQuarters.problem(starts.dropLast(1)))                         // 239
        assertNotNull(HizbQuarters.problem(starts.toMutableList().also { it[10] = it[9] }))   // not increasing
        assertNotNull(HizbQuarters.problem(starts.toMutableList().also { it[8] = 2 to 143 })) // juz 2 does not start a hizb
        assertNull(HizbQuarters.from(emptyList()))
    }

    @Test fun partsAndHizbNumbersFollowTheQuarterIndex() {
        assertEquals(HizbLabel(1, 0, 1), HizbQuarters.labelOf(1))
        assertEquals(HizbLabel(1, 3, 4), HizbQuarters.labelOf(4))
        assertEquals(HizbLabel(2, 0, 5), HizbQuarters.labelOf(5))
        assertEquals(HizbLabel(60, 3, 240), HizbQuarters.labelOf(240))
    }
}
