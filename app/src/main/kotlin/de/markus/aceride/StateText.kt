package de.markus.aceride

import android.os.SystemClock
import de.markus.aceride.core.CameraAdapter.Recording
import de.markus.aceride.core.Controller
import de.markus.aceride.core.DisplayValues

object StateText {
    fun title(s: Controller.State?): String {
        if (s == null) return UiText.get(R.string.status_unknown)
        if (s.power() == Controller.Power.OFF_REQUESTED) return UiText.get(R.string.status_off_requested)
        if (s.busy() && !s.background() && s.message().startsWith("Aufwecken")) return UiText.get(R.string.status_waking)
        return when (s.recording()) {
            Recording.RECORDING -> "● REC"
            Recording.IDLE -> if (s.pauseIntent()) UiText.get(R.string.status_pause) else UiText.get(R.string.status_stopped)
            Recording.TRANSITION -> UiText.get(R.string.status_transition)
            else -> UiText.get(R.string.control_unknown)
        }
    }
    fun connection(s: Controller.State?): String = when (s?.connection()) {
        Controller.Connection.CONNECTED -> UiText.get(R.string.connection_connected)
        Controller.Connection.CONNECTING -> if (s.message().startsWith("Aufwecken")) UiText.get(R.string.connection_waking) else UiText.get(R.string.connection_connecting)
        else -> UiText.get(R.string.control_disconnected)
    }
    fun battery(s: Controller.State?) = s?.battery()?.let { UiText.get(R.string.battery_percent, it) } ?: UiText.get(R.string.control_battery_unknown)
    fun duration(s: Controller.State?) = DisplayValues.duration(s?.seconds(), s?.durationEstimated() == true)
    fun storage(s: Controller.State?) = DisplayValues.storage(s?.storage(), UiLanguage.current())
    fun message(s: Controller.State?): String {
        if (!SessionRuntime.serviceRunning) return UiText.get(R.string.open_or_choose_action)
        if (s?.camera()?.model() == de.markus.aceride.core.CameraModels.GO && s.connection() == Controller.Connection.CONNECTING)
            return UiText.get(R.string.go_authorize)
        if (s?.recording() == Recording.UNKNOWN && s.lastConfirmed() != Recording.UNKNOWN)
            return "${UiText.message(s.message())}\n${detail(s)}"
        return s?.message()?.let { UiText.message(it) } ?: UiText.get(R.string.control_select_first)
    }
    fun detail(s: Controller.State?): String {
        if (s == null || !SessionRuntime.serviceRunning) return UiText.get(R.string.open_before_ride)
        val connection = when (s.connection()) {
            Controller.Connection.CONNECTED -> UiText.get(R.string.connection_connected)
            Controller.Connection.CONNECTING -> UiText.get(R.string.connection_connecting)
            else -> UiText.get(R.string.connection_disconnected)
        }
        if (s.recording() == Recording.UNKNOWN) {
            val age = if (s.lastConfirmedAt() >= 0)
                ((SystemClock.elapsedRealtime()-s.lastConfirmedAt()).coerceAtLeast(0) / 1000).toString() else null
            val previous = when (s.lastConfirmed()) {
                Recording.RECORDING -> UiText.get(R.string.previous_recording); Recording.IDLE -> UiText.get(R.string.previous_stopped); else -> UiText.get(R.string.status_unknown)
            }
            return if (age == null) UiText.get(R.string.last_confirmed, connection, previous)
                else UiText.get(R.string.last_confirmed_age, connection, previous, age)
        }
        val battery = s.battery()?.let { "${it}%" } ?: UiText.get(R.string.battery_unknown)
        return "$connection · $battery · ${storage(s)} · ${duration(s)}"
    }
    fun primary(s: Controller.State?): Controller.Action = when {
        s == null -> Controller.Action.REFRESH
        s.power() == Controller.Power.OFF_REQUESTED -> Controller.Action.WAKE
        s.recording() == Recording.RECORDING -> Controller.Action.STOP
        s.recording() == Recording.UNKNOWN || s.recording() == Recording.TRANSITION -> Controller.Action.REFRESH
        s.pauseIntent() -> Controller.Action.RESUME
        else -> Controller.Action.START
    }
    fun label(action: Controller.Action) = when (action) {
        Controller.Action.START -> UiText.get(R.string.control_play); Controller.Action.STOP -> UiText.get(R.string.control_stop)
        Controller.Action.PAUSE -> UiText.get(R.string.control_pause); Controller.Action.RESUME -> UiText.get(R.string.control_resume)
        Controller.Action.WAKE -> UiText.get(R.string.control_wake); Controller.Action.CONNECT -> UiText.get(R.string.control_connect)
        Controller.Action.REFRESH -> UiText.get(R.string.control_refresh)
        Controller.Action.POWER_OFF -> UiText.get(R.string.control_power)
    }
}

