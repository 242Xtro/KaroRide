package de.markus.aceride

import android.content.Context
import de.markus.aceride.core.CameraAdapter.Status
import de.markus.aceride.core.Insta360V2Adapter.V2Api
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

object SdkFactory {
    fun create(context: Context): V2Api = CameraBackend.create(context.applicationContext)
}
internal class SdkAccessRequired : V2Api {
    private fun <T> missing(): CompletableFuture<T> = CompletableFuture.failedFuture(
        IllegalStateException("Hersteller-SDK und Geräteanbindung fehlen. Dies ist eine Vorbereitung, keine Kamerasteuerung."))
    override fun connectBle(address: String) = missing<Void>()
    override fun checkAuthorization() = missing<Boolean>()
    override fun fetchExactModel() = missing<String>()
    override fun loadCaptureJson() = missing<Void>()
    override fun readFreshCaptureAndMode() = missing<Status>()
    override fun bleWakeUp(exactModel: String, bleName: String) = missing<Void>()
    override fun bleSupportsOriginalAcePro() = false
    override fun advertiserAvailable() = false
    override fun startCapture() = missing<Void>()
    override fun stopCapture() = missing<Void>()
    override fun shutdown() = missing<Void>()
    override fun register(status: Consumer<Status>, disconnect: Consumer<String>) = AutoCloseable {}
    override fun disconnectBle() {}
    override fun close() {}
}

