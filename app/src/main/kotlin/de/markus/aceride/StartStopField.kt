package de.markus.aceride

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicBoolean
import de.markus.aceride.core.CameraAdapter.Recording
import de.markus.aceride.core.Controller
import de.markus.aceride.core.FieldControls
import de.markus.aceride.core.CameraModels
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig

/** One whole-field touch target alongside other ride data, without opening the app in a live session. */
internal class StartStopField : DataTypeImpl("aceride", "start-stop") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val handler = Handler(Looper.getMainLooper())
        val density = context.resources.displayMetrics.density
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val active = AtomicBoolean(true)
        val redraw = object : Runnable {
            override fun run() {
                if (!active.get()) return
                val state = SessionRuntime.controller?.state()
                val camera = state?.camera() ?: CameraStore(context).load()
                val selected = camera != null
                val decision = FieldControls.decide(state, SessionRuntime.serviceRunning, selected, config.preview)
                val rec = !config.preview && SessionRuntime.serviceRunning && state?.recording() == Recording.RECORDING
                val label = when {
                    config.preview -> UiText.get(R.string.field_start_stop)
                    !decision.enabled() -> UiText.get(R.string.field_wait)
                    decision.action() == null -> UiText.get(R.string.field_setup)
                    else -> StateText.label(decision.action())
                }
                val status = when {
                    config.preview || !selected -> UiText.get(R.string.field_camera)
                    !SessionRuntime.serviceRunning -> UiText.get(R.string.control_disconnected)
                    rec -> "● REC · ${StateText.duration(state).removePrefix("Clip: ")}"
                    state?.recording() == Recording.IDLE -> if (state.pauseIntent()) UiText.get(R.string.status_pause) else UiText.get(R.string.connection_connected)
                    else -> StateText.title(state)
                }
                val icon = when (decision.action()) {
                    Controller.Action.STOP -> R.drawable.ic_stop
                    Controller.Action.WAKE -> R.drawable.ic_wake
                    Controller.Action.CONNECT, Controller.Action.REFRESH -> R.drawable.ic_connect
                    else -> R.drawable.ic_play
                }
                val views = RemoteViews(context.packageName, R.layout.ride_start_stop).apply {
                    setTextViewText(R.id.toggle_state, if (!config.preview && camera != null) "${CameraModels.label(camera.model())} · $status" else status)
                    setTextViewText(R.id.toggle_action, label)
                    setImageViewResource(R.id.toggle_icon, icon)
                    setInt(R.id.toggle_root, "setBackgroundColor", if (rec) Color.rgb(177, 14, 35) else Color.rgb(9, 11, 15))
                    setTextColor(R.id.toggle_action, if (decision.enabled() || config.preview) Color.WHITE else Color.rgb(150, 155, 165))
                    setViewVisibility(R.id.toggle_state, if (config.viewSize.second >= 64*density) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.toggle_icon, if (!config.preview && decision.enabled() &&
                        config.viewSize.first >= 92*density && config.viewSize.second >= 48*density &&
                        (label.length <= 6 || config.viewSize.first >= 120*density)) View.VISIBLE else View.GONE)
                    setBoolean(R.id.toggle_root, "setEnabled", decision.enabled())
                    setOnClickPendingIntent(R.id.toggle_root, if (decision.enabled())
                        CameraIntents.click(context, decision.action(), decision.openApp(), camera) else null)
                }
                emitter.updateView(views)
                if (active.get()) handler.postDelayed(this, 1050)
            }
        }
        handler.post(redraw)
        emitter.setCancellable { active.set(false); handler.removeCallbacks(redraw) }
    }
}
