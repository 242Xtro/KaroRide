package de.markus.aceride

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.markus.aceride.core.Controller

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Private receiver. Each immutable PendingIntent contains an explicit action.
        val action = runCatching { Controller.Action.valueOf(intent.getStringExtra("cameraAction") ?: "") }.getOrNull()
        val address = intent.getStringExtra("cameraAddress")
        if (action != null && !address.isNullOrBlank()) SessionRuntime.action(action, address)
    }
}

