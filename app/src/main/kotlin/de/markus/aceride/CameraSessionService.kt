package de.markus.aceride

import android.app.*
import android.content.Intent
import android.os.IBinder
import de.markus.aceride.core.Controller
import de.markus.aceride.core.CameraModels
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.RequestBluetooth
import io.hammerhead.karooext.models.ReleaseBluetooth
import java.util.function.Consumer

class CameraSessionService : Service() {
    private lateinit var system: KarooSystemService
    private var owner: Controller? = null
    private val languageObserver = Runnable {
        owner?.let { ctl ->
            if (SessionRuntime.serviceRunning) {
                updateChannel()
                startForeground(1, notification("${ctl.state().camera()?.let { CameraModels.label(it.model()) } ?: UiText.get(R.string.notification_camera)} · ${StateText.title(ctl.state())}"))
            }
        }
    }
    private val observer = Consumer<Controller.State> { state ->
        // Updating our existing foreground notification requires no notification access.
        // Android permits foreground services when POST_NOTIFICATIONS is not granted.
        startForeground(1, notification("${state.camera()?.let { CameraModels.label(it.model()) } ?: UiText.get(R.string.notification_camera)} · ${StateText.title(state)}"))
    }
    override fun onCreate() {
        super.onCreate()
        if (!SdkConsent.accepted(this)) { stopSelf(); return }
        updateChannel()
        startForeground(1, notification(UiText.get(R.string.notification_unknown)))
        SessionRuntime.serviceRunning = true
        val ctl = SessionRuntime.create(this)
        owner = ctl
        ctl.observe(observer)
        UiLanguage.observe(languageObserver)
        system = KarooSystemService(this)
        system.connect { connected -> if (connected) system.dispatch(RequestBluetooth("aceride-session")) }
    }
    private fun updateChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("session", UiText.get(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(text: String): Notification = Notification.Builder(this, "session")
        .setSmallIcon(R.drawable.ic_aceride).setContentTitle("KaroRide").setContentText(text)
        .setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SdkConsent.accepted(this)) stopSelf()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        UiLanguage.removeObserver(languageObserver)
        owner?.removeObserver(observer)
        SessionRuntime.close(owner)
        if (::system.isInitialized) {
            system.dispatch(ReleaseBluetooth("aceride-session"))
            system.disconnect()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

