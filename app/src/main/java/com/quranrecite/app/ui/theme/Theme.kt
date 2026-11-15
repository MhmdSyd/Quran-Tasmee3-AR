package com.quranrecite.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.quranrecite.app.R

// Palette taken from Quran_Recitation_App_Design.html
val Ink = Color(0xFF1F2A24)
val Muted = Color(0xFF5B6258)
val Paper = Color(0xFFFFFDF6)
val Gold = Color(0xFFD9C48A)
val GoldDark = Color(0xFFB8923A)
val Green = Color(0xFF1F6B4F)
val GreenDark = Color(0xFF17513C)
val GreenTint = Color(0xFFE3EFE7)
val MistakeRed = Color(0xFFA33A2B)
val MistakeTint = Color(0xFFF6D9D3)

val HafsFont = FontFamily(Font(R.font.uthmanic_hafs))

/** header-surah-name.ttf: ONLY for surah names/headings (ligature/PUA glyph per surah), never for ayah text. */
val SurahNameFont = FontFamily(Font(R.font.header_surah_name))

@Composable
fun QuranTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Paper,
            secondary = Gold, onSecondary = Ink,
            background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink,
            error = MistakeRed, onError = Paper,
        ),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
