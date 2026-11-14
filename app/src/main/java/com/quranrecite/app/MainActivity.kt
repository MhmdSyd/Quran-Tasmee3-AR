package com.quranrecite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quranrecite.app.ui.AppNav
import com.quranrecite.app.ui.theme.QuranTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as QuranApp).container
        setContent {
            QuranTheme { AppNav(container) }
        }
    }
}
