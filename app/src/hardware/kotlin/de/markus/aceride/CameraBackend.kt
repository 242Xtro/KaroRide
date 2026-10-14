package de.markus.aceride

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.arashivision.sdk.camera.InstaCameraSDK
import com.arashivision.sdk.common.log.LogLevel
import com.arashivision.common.BleLog
import de.markus.aceride.core.Insta360V2Adapter.V2Api
import java.io.File

object CameraBackend {
    @Volatile private var failure: Throwable? = null
    @Volatile private var initialized = false
    @Synchronized fun initialize(application: Application) {
        if (initialized || failure != null) return
        if (!SdkConsent.accepted(application)) return
        // The provided Demo initializes native BLE only after permission approval.
        if (application.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            application.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        try {
            InstaCameraSDK.init(application) {
                fileDir = File(application.filesDir, "insta").absolutePath
                cacheDir = File(application.cacheDir, "insta").absolutePath
                logLevel = LogLevel.ERROR
            }
            BleLog.enableLog(false)
            initialized = true
        } catch (ex: Throwable) {
            failure = ex
        }
    }
    fun create(context: Context): V2Api {
        check(SdkConsent.accepted(context)) { "KaroRide öffnen und die SDK-Hinweise bestätigen." }
        initialize(context.applicationContext as Application)
        failure?.let { throw IllegalStateException("Kamera-SDK konnte nicht geladen werden. Gerätefähigkeiten prüfen (${it.javaClass.simpleName}).") }
        check(initialized) { "Bluetooth-Berechtigungen fehlen. KaroRide öffnen und erlauben." }
        return VendorBinding(context.applicationContext)
    }
}
