package com.quranrecite.app.speech.asr

/**
 * The letter space the model writes in. Ported 1:1 from the model author's `retrieve.py`
 * (huggingface.co/Abdou/quran-asr-small): harakat and Quranic marks are dropped and letter variants folded to the
 * 29 letters of tokens.txt. Applying [fold] to canonical Hafs words gives the string the model "should" produce for
 * them, which is what Agent 5 needs for matching. This is alphabet normalisation only; there is NO word matching here.
 *
 * Example: ٱلسَّمَٰوَٰتِ -> السموت, ءَامَنُوا۟ -> ءامنوا, ٱلۡكِتَٰبِ -> الكتب.
 */
object ModelAlphabet {
    /** ء ا ب ت ث ج ح خ د ذ ر ز س ش ص ض ط ظ ع غ ف ق ك ل م ن ه و ي (same order as tokens.txt ids 1..29). */
    val LETTERS: Set<Char> = ("\u0621\u0627\u0628\u062A\u062B\u062C\u062D\u062E\u062F\u0630\u0631\u0632\u0633\u0634" +
        "\u0635\u0636\u0637\u0638\u0639\u063A\u0641\u0642\u0643\u0644\u0645\u0646\u0647\u0648\u064A").toSet()

    private val FOLD: Map<Char, Char> = mapOf(
        '\u0623' to '\u0627', // أ -> ا
        '\u0625' to '\u0627', // إ -> ا
        '\u0622' to '\u0627', // آ -> ا
        '\u0671' to '\u0627', // ٱ -> ا
        '\u0672' to '\u0627', // ٲ -> ا
        '\u0673' to '\u0627', // ٳ -> ا
        '\u0649' to '\u064A', // ى -> ي
        '\u0626' to '\u064A', // ئ -> ي
        '\u06D2' to '\u064A', // ے -> ي
        '\u06CC' to '\u064A', // ی -> ي
        '\u0624' to '\u0648', // ؤ -> و
        '\u0629' to '\u0647', // ة -> ه
        '\u06A9' to '\u0643', // ک -> ك
    )

    /** Folds one word to model letters; everything that is not a model letter (diacritics, marks, digits) is dropped. */
    fun foldWord(word: String): String {
        val sb = StringBuilder(word.length)
        for (ch in word) {
            val c = FOLD[ch] ?: ch
            if (c in LETTERS) sb.append(c)
        }
        return sb.toString()
    }

    /** Splits raw recognizer output (or any text) into folded words; empty results (pure marks) are dropped. */
    fun foldWords(text: String): List<String> =
        text.split(' ', '\u00A0', '\t', '\n', AsrModel.WORD_SEPARATOR).mapNotNull { w -> foldWord(w).ifEmpty { null } }

    fun foldText(text: String): String = foldWords(text).joinToString(" ")
}
