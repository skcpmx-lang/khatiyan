package com.shohan.khatiyan

import android.app.Application
import com.shohan.khatiyan.di.AppContainer

/** Application entry: owns the DI container, notification channel and reminder chain. */
class KhatiyanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppCreate()
    }
}
