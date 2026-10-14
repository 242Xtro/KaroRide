package de.markus.aceride

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import de.markus.aceride.core.Controller
import de.markus.aceride.core.RideControls
import de.markus.aceride.core.RideControls.Slot
import de.markus.aceride.core.CameraModels

/** Same six controls and action policy in the activity and full-size ride field. */
object ControlViews {
    data class Item(val slot: Slot, val view: Int, val label: Int, val icon: Int)
    val items = listOf(
        Item(Slot.WAKE, R.id.control_wake, R.id.control_wake_label, R.id.control_wake_icon),
        Item(Slot.CONNECT, R.id.control_connect, R.id.control_connect_label, R.id.control_connect_icon),
        Item(Slot.START, R.id.control_play, R.id.control_play_label, R.id.control_play_icon),
        Item(Slot.PAUSE, R.id.control_pause, R.id.control_pause_label, R.id.control_pause_icon),
        Item(Slot.STOP, R.id.control_stop, R.id.control_stop_label, R.id.control_stop_icon),
        Item(Slot.POWER_OFF, R.id.control_power, R.id.control_power_label, R.id.control_power_icon))
    fun update(activity: Activity, state: Controller.State?) {
        val selected = CameraStore(activity).load() != null
        for (item in items) {
            val enabled = RideControls.enabled(item.slot, state, SessionRuntime.serviceRunning, selected) ||
                (!SessionRuntime.serviceRunning && selected && item.slot in listOf(Slot.WAKE, Slot.CONNECT, Slot.START))
            val label = if (item.slot == Slot.POWER_OFF && state?.camera()?.model() == CameraModels.GO) UiText.get(R.string.power_off_on_device) else StateText.label(RideControls.action(item.slot, state))
            activity.findViewById<View>(item.view).apply {
                isEnabled = enabled; contentDescription = label; alpha = if (enabled) 1f else .48f
            }
            activity.findViewById<TextView>(item.label).text = label
            if (item.slot == Slot.START) activity.findViewById<ImageView>(item.icon).setImageResource(R.drawable.ic_play)
        }
    }
}
