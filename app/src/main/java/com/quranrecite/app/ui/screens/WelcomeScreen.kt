package com.quranrecite.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.quranrecite.app.R
import kotlinx.coroutines.delay

/**
 * Welcome page: plain white background with the Basmala image, shown for ~2.2 s (tap to skip), then the reader opens.
 * The image is a black-ink PNG with transparent background (res/drawable-nodpi/bismillah.png).
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val done by rememberUpdatedState(onContinue)
    LaunchedEffect(Unit) {
        delay(2200)
        done()
    }
    Box(
        Modifier.fillMaxSize().background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.bismillah),
            contentDescription = "بسم الله الرحمن الرحيم",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}
