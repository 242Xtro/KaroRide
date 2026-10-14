package de.markus.aceride

import android.app.Application

class AceRideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UiLanguage.initialize(this)
        CameraBackend.initialize(this)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        UiLanguage.initialize(this)
    }
}
