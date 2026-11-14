package com.quranrecite.app

import android.app.Application
import com.quranrecite.app.di.AppContainer

class QuranApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
