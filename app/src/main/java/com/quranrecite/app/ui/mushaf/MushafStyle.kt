package com.quranrecite.app.ui.mushaf

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import com.quranrecite.app.ui.theme.Gold
import com.quranrecite.app.ui.theme.GoldDark
import com.quranrecite.app.ui.theme.Green
import com.quranrecite.app.ui.theme.GreenDark
import com.quranrecite.app.ui.theme.HafsFont
import com.quranrecite.app.ui.theme.Ink
import com.quranrecite.app.ui.theme.Muted
import com.quranrecite.app.ui.theme.MistakeRed
import com.quranrecite.app.ui.theme.MistakeTint

/** Colors and font of the mushaf. Defaults are taken from the design boards. */
@Immutable
data class MushafStyle(
    val ink: Color = Ink,
    val verseNumber: Color = Color(0xFF8A6D1F),
    val correct: Color = GreenDark,
    val currentText: Color = GreenDark,
    val currentFill: Color = Color(0xFFD7EADD),
    val uncertainText: Color = Muted,
    val uncertainFill: Color = Gold.copy(alpha = 0.28f),
    val uncertainLine: Color = GoldDark,
    val hiddenLine: Color = Color(0x738A8A8A),   // thin gray, ~45% opacity
    val expectedLine: Color = Green,
    val skippedLine: Color = GoldDark,
    val mistakeLine: Color = MistakeRed,
    val mistakeFill: Color = MistakeTint,
    /** null = surah_header_bg.png exactly as supplied. A tint keeps the PNG's alpha. */
    val headerTint: Color? = null,
    val fontFamily: FontFamily = HafsFont,
) {
    /** The only text style used for Quran text: exact supplied font, no synthetic bold/italic, Arabic locale. */
    fun textStyle(fontSize: TextUnit, color: Color = ink) = TextStyle(
        color = color,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
        fontSynthesis = FontSynthesis.None,
        lineHeight = fontSize * MushafFit.LINE_HEIGHT_EM,
        textDirection = TextDirection.Rtl,
        localeList = LocaleList("ar"),
    )

    companion object {
        val Default = MushafStyle()
    }
}
