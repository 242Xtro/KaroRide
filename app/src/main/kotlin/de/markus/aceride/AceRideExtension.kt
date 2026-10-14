package de.markus.aceride

import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicBoolean
import de.markus.aceride.core.CameraAdapter.Recording
import de.markus.aceride.core.Controller
import de.markus.aceride.core.RideControls
import de.markus.aceride.core.CameraModels
import de.markus.aceride.core.CameraAdapter.Camera
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.karooext.models.UpdateGraphicConfig

class AceRideExtension : KarooExtension("aceride", BuildConfig.VERSION_NAME) {
    override val types: List<DataTypeImpl> by lazy { listOf(AceField(false), AceField(true), StartStopField()) }
    override fun onBonusAction(actionId: String) {
        val action = when (actionId) {
            "start" -> Controller.Action.START; "stop" -> Controller.Action.STOP
            "pause" -> Controller.Action.PAUSE; "resume" -> Controller.Action.RESUME
            "wake" -> Controller.Action.WAKE
            "power-off" -> Controller.Action.POWER_OFF
            else -> return
        }
        SessionRuntime.action(action)
    }
}
private class AceField(private val controls: Boolean) : DataTypeImpl("aceride", if (controls) "control" else "status") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val handler = Handler(Looper.getMainLooper())
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val active = AtomicBoolean(true)
        val redraw = object : Runnable {
            override fun run() {
                if (!active.get()) return
                val state = SessionRuntime.controller?.state()
                val camera = state?.camera() ?: CameraStore(context).load()
                val cameraName = camera?.let { CameraModels.label(it.model()) } ?: "KaroRide"
                val recording = state?.recording() == Recording.RECORDING && SessionRuntime.serviceRunning
                val full = controls && config.viewSize.first >= dp(context, 210) && config.viewSize.second >= dp(context, 240)
                val compact = controls && !full && config.viewSize.first >= dp(context, 210) && config.viewSize.second >= dp(context, 112)
                val views = RemoteViews(context.packageName, R.layout.ride_control).apply {
                    setTextViewText(R.id.field_state, "$cameraName ▾ · ${StateText.title(state)}")
                    setTextViewText(R.id.field_detail, if (full && state?.recording() == Recording.UNKNOWN)
                        "${StateText.detail(state)} · ${UiText.get(R.string.control_battery_unknown)}"
                        else if (full)
                        "${StateText.connection(state)} · ${StateText.battery(state)}\n${StateText.storage(state)} · ${StateText.duration(state)}"
                        else if (controls && !compact) UiText.get(R.string.field_tap_controls) else StateText.detail(state))
                    setInt(R.id.field_root, "setBackgroundColor", if (recording) Color.rgb(177, 14, 35) else Color.rgb(9, 11, 15))
                    setTextColor(R.id.field_state, Color.WHITE)
                    setTextColor(R.id.field_detail, Color.WHITE)
                    setViewVisibility(R.id.field_buttons, if (full) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.field_compact, if (compact) View.VISIBLE else View.GONE)
                    setOnClickPendingIntent(R.id.field_root, if (config.preview) null else open(context))
                    setOnClickPendingIntent(R.id.field_state, if (config.preview) null else CameraIntents.choose(context))
                    for (item in ControlViews.items) {
                        val action = RideControls.action(item.slot, state)
                        val label = if (item.slot == RideControls.Slot.POWER_OFF && camera?.model() == CameraModels.GO) UiText.get(R.string.power_off_on_device) else StateText.label(action)
                        val enabled = !config.preview && RideControls.enabled(item.slot, state, SessionRuntime.serviceRunning, camera != null)
                        setTextViewText(item.label, label)
                        setContentDescription(item.view, label)
                        setTextColor(item.label, if (enabled) Color.WHITE else Color.rgb(120, 125, 135))
                        setInt(item.icon, "setImageAlpha", if (enabled) 255 else 100)
                        setBoolean(item.view, "setEnabled", enabled)
                        val needsPermission = action == Controller.Action.WAKE &&
                            camera?.model() != CameraModels.GO && context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        setOnClickPendingIntent(item.view, if (config.preview) null
                            else if (needsPermission) open(context, action, camera) else command(context, action, camera))
                    }
                    val primary = StateText.primary(state)
                    setTextViewText(R.id.field_primary, StateText.label(primary))
                    setTextViewText(R.id.field_pause, UiText.get(R.string.control_pause))
                    setTextViewText(R.id.field_wake, UiText.get(R.string.control_stop))
                    val enabled = !config.preview && state != null && (!state.busy() || state.background()) && SessionRuntime.serviceRunning
                    for (id in listOf(R.id.field_primary, R.id.field_pause, R.id.field_wake)) setBoolean(id, "setEnabled", enabled)
                    setBoolean(R.id.field_pause, "setEnabled", enabled && recording)
                    if (!config.preview) {
                        setOnClickPendingIntent(R.id.field_primary, CameraIntents.click(context, primary, camera = camera))
                        setOnClickPendingIntent(R.id.field_pause, command(context, Controller.Action.PAUSE, camera))
                        setOnClickPendingIntent(R.id.field_wake, command(context, Controller.Action.STOP, camera))
                    } else {
                        for (id in listOf(R.id.field_primary, R.id.field_pause, R.id.field_wake))
                            setOnClickPendingIntent(id, null)
                    }
                }
                emitter.updateView(views)
                if (active.get()) handler.postDelayed(this, 1050) // karoo-ext drops faster RemoteViews updates.
            }
        }
        handler.post(redraw)
        emitter.setCancellable { active.set(false); handler.removeCallbacks(redraw) }
    }
    private fun dp(context: Context, value: Int) = value * context.resources.displayMetrics.density
    private fun open(context: Context, action: Controller.Action? = null, camera: Camera? = CameraStore(context).load()): PendingIntent {
        return CameraIntents.open(context, action, camera)
    }
    private fun command(context: Context, action: Controller.Action, camera: Camera?): PendingIntent =
        CameraIntents.command(context, action, camera)
}
