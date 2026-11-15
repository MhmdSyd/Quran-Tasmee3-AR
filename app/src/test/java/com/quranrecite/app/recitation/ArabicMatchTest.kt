package com.quranrecite.app.recitation

import com.quranrecite.app.recitation.ArabicMatch.Match
import com.quranrecite.app.settings.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicMatchTest {
    private val n = Strictness.Normal

    @Test fun foldingKeepsCanonicalLettersOnly() {
        assertEquals("السموت", ArabicMatch.fold("ٱلسَّمَٰوَٰتِ"))
        assertEquals("ءامنوا", ArabicMatch.fold("ءَامَنُوا۟"))
        assertEquals("الكتب", ArabicMatch.fold("ٱلۡكِتَٰبِ"))
    }

    @Test fun alefHamzaAndYaVariantsAreTheSameWord() {
        // أ إ آ ٱ -> ا ; ى ئ -> ي ; ؤ -> و ; ة -> ه
        assertEquals(1.0, ArabicMatch.similarity(ArabicMatch.fold("أَنزَلَ"), ArabicMatch.fold("انزل")), 0.0)
        assertEquals(1.0, ArabicMatch.similarity(ArabicMatch.fold("إِلَىٰ"), ArabicMatch.fold("الي")), 0.0)
        assertEquals(1.0, ArabicMatch.similarity(ArabicMatch.fold("مُؤۡمِنِينَ"), ArabicMatch.fold("مومنين")), 0.0)
    }

    @Test fun shortWordsMustBeExactInEveryMode() {
        for (s in Strictness.values()) {
            assertEquals(Match.Strong, ArabicMatch.classify("من", "من", s))
            assertTrue("من/ما must not match in $s", ArabicMatch.classify("من", "ما", s) == Match.Miss)
            assertTrue("لا/ما must not match in $s", ArabicMatch.classify("لا", "ما", s) == Match.Miss)
        }
    }

    @Test fun oneEditInALongWordIsAcceptedInNormalButOnlyWeakInStrict() {
        // يعلمون / تعلمون: a real pair of look-alike Quranic words, one letter apart
        assertEquals(Match.Strong, ArabicMatch.classify("يعلمون", "تعلمون", Strictness.Normal))
        assertEquals(Match.Weak, ArabicMatch.classify("يعلمون", "تعلمون", Strictness.Strict))
    }

    @Test fun twoEditsInAMediumWordAreOnlyWeak() {
        val w = "مصلحون"
        val twoEdits = mangle(w, 1, 3)
        assertEquals(Match.Weak, ArabicMatch.classify(twoEdits, w, n))
    }

    @Test fun unrelatedWordsMiss() {
        // الرحمن / الرحيم: close enough that Normal calls them alike (known trade-off, see handoff); Strict does not
        assertTrue(ArabicMatch.classify("الرحمن", "الرحيم", Strictness.Strict) != Match.Strong)
        assertEquals(Match.Miss, ArabicMatch.classify("كتاب", "ملائكة", n))
    }

    @Test fun confusableLettersCostLessThanArbitraryOnes() {
        assertTrue(ArabicMatch.similarity("سبع", "صبع") > ArabicMatch.similarity("سبع", "شبع"))
    }

    @Test fun similarityIsSymmetric() {
        val a = "فزادهم"; val b = "فزادهمو"
        assertEquals(ArabicMatch.similarity(a, b), ArabicMatch.similarity(b, a), 1e-9)
    }
}
