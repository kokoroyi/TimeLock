package com.timelock.app

import android.app.Application
import com.timelock.app.di.AppContainer

class TimeLockApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
