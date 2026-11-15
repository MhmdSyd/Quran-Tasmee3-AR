package com.quranrecite.app.session

import com.quranrecite.app.recitation.RecitationFeedback
import com.quranrecite.app.recitation.ReviewReason

/** Arabic UI strings for the recitation/summary screens. Neutral wording: nothing here is a Tajweed or pronunciation verdict. */
object SummaryText {
    fun digits(n: Int): String = n.toString().map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")

    fun reason(r: ReviewReason): String = when (r) {
        ReviewReason.NotRecognized -> "لم يتأكد التعرف عليها"
        ReviewReason.PossiblySkipped -> "ربما تُركت"
        ReviewReason.Uncertain -> "التعرف عليها غير مؤكَّد"
        ReviewReason.Hinted -> "استعنتَ بالتلميح"
        ReviewReason.Manual -> "أظهرتَها يدويًا"
        ReviewReason.SelfCorrected -> "أعدتَها بعد أن فاتت"
        ReviewReason.ManuallySkipped -> "تجاوزتَها يدويًا"
    }

    fun location(ayah: Int, indexInAyah: Int) = "الآية ${digits(ayah)} · الكلمة ${digits(indexInAyah)}"

    /** 154000 -> "٢:٣٤" */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val sec = (s % 60).toInt()
        return digits(m.toInt()) + ":" + (if (sec < 10) "٠" else "") + digits(sec)
    }

    fun feedback(f: RecitationFeedback): String? = when (f) {
        RecitationFeedback.None -> null
        RecitationFeedback.Uncertain -> "لم يتأكد التعرف على كلمة — يمكنك إعادتها"
        RecitationFeedback.PossibleSkip -> "ربما تُركت كلمة — اضغط على كلمة لتحديد موضعك"
        RecitationFeedback.PossibleRepetition -> "تكرار — أكمل من حيث توقفت"
        RecitationFeedback.NoMatch -> "لم أتعرف على هذا المقطع في الصفحة"
    }
}
