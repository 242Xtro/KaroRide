package de.markus.aceride

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import de.markus.aceride.core.Controller
import de.markus.aceride.core.CameraModels
import android.net.Uri
import de.markus.aceride.core.CameraAdapter.Camera

/** Immutable explicit actions. Activity startup handles permissions and foreground lifecycle. */
internal object CameraIntents {
    fun choose(context: Context): PendingIntent = PendingIntent.getActivity(context, 999,
        Intent(context, MainActivity::class.java).putExtra("chooseCamera", true)
            .setData(Uri.parse("aceride://camera/choose"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun open(context: Context, action: Controller.Action? = null, camera: Camera? = CameraStore(context).load()): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action?.let {
                putExtra("cameraAction", it.name)
                camera?.address()?.let { address ->
                    putExtra("cameraAddress", address)
                    data = Uri.parse("aceride://camera/${Uri.encode(address)}/${action.name}")
                }
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // An ordinary open must never reuse an action-bearing PendingIntent.
        return PendingIntent.getActivity(context, 1000 + (action?.ordinal?.plus(1) ?: 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun command(context: Context, action: Controller.Action, camera: Camera? = CameraStore(context).load()): PendingIntent {
        val address = camera?.address() ?: ""
        val intent = Intent(context, ActionReceiver::class.java).putExtra("cameraAction", action.name)
            .putExtra("cameraAddress", address).setData(Uri.parse("aceride://camera/${Uri.encode(address)}/${action.name}"))
        return PendingIntent.getBroadcast(context, action.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun click(context: Context, action: Controller.Action?, openApp: Boolean = false, camera: Camera? = CameraStore(context).load()): PendingIntent {
        val base = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val wake = action != Controller.Action.WAKE || camera?.model() == CameraModels.GO ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        return if (action == null || openApp || !base || !wake) open(context, action, camera) else command(context, action, camera)
    }
}
