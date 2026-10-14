package de.markus.aceride

import android.app.Application
import android.content.Context
import de.markus.aceride.core.Insta360V2Adapter.V2Api

object CameraBackend {
    fun initialize(application: Application) {}
    fun create(context: Context): V2Api = SdkAccessRequired()
}
