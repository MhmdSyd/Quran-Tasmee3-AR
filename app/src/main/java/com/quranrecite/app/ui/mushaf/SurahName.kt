package com.quranrecite.app.ui.mushaf

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.TextUnit
import com.quranrecite.app.ui.theme.SurahNameFont

/**
 * Surah names (headings, reader title, surah list) are drawn with header-surah-name.ttf. That font has ONE glyph per
 * surah, the whole "سورة <name>", at U+E000 + surah number (surah 2 = U+E002 ... surah 114 = U+E072; verified against the
 * font's cmap). It has no glyph for surah 1 (Al-Fatiha): that name falls back to the Quran font, see [SurahNameText].
 */
object SurahNameGlyph {
    fun of(surah: Int): String? = if (surah in 2..114) (0xE000 + surah).toChar().toString() else null
}

/** Never used for ayah text. [nameAr] is the plain name from the database ("البقرة"). */
@Composable
fun SurahNameText(surah: Int, nameAr: String, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val glyph = SurahNameGlyph.of(surah)
    if (glyph != null) {
        Text(
            text = glyph,
            modifier = modifier,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontFamily = SurahNameFont,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Normal,
                fontSynthesis = FontSynthesis.None,
                localeList = LocaleList("ar"),
            ),
        )
    } else {
        // Surah 1 only: no glyph in header-surah-name.ttf, so the (already used) Quran font writes "سورة الفاتحة".
        Text("سورة $nameAr", modifier = modifier, maxLines = 1, softWrap = false, style = MushafStyle.Default.textStyle(fontSize * FALLBACK_SCALE, color))
    }
}

private const val FALLBACK_SCALE = 0.72f
