package de.markus.aceride

import android.content.Context
import android.os.SystemClock
import de.markus.aceride.core.Controller
import de.markus.aceride.core.Insta360V2Adapter
import de.markus.aceride.core.CameraAdapter.Camera

object SessionRuntime {
    @Volatile var controller: Controller? = null
        private set
    @Volatile var serviceRunning = false
    @Synchronized fun create(context: Context): Controller {
        controller?.let { return it }
        val store = CameraStore(context)
        lateinit var value: Controller
        value = Controller(Insta360V2Adapter(SdkFactory.create(context)),
            { SystemClock.elapsedRealtime() }, Controller.Timing.production(), store.pause(), { paused ->
                value.selectedCamera()?.let { store.pause(it.address(), paused) }
            })
        store.load()?.let { value.restore(it) }
        controller = value
        return value
    }
    @Synchronized fun select(context: Context, camera: Camera) {
        val store = CameraStore(context)
        val ctl = create(context)
        if (ctl.selectedCamera()?.address() == camera.address()) {
            check(ctl.selectedCamera()?.model() == camera.model()) { "Gespeichertes Kameramodell stimmt nicht überein" }
            return
        }
        ctl.switchCamera(camera, store.pause(camera.address())) { store.save(camera) }
    }
    @Synchronized fun close(expected: Controller? = controller) {
        expected?.close()
        if (controller === expected) { controller = null; serviceRunning = false }
    }
    fun action(action: Controller.Action, expectedAddress: String? = null) {
        // Do not start a foreground service from an invisible field callback.
        val ctl = controller ?: return
        synchronized(ctl) {
            if (serviceRunning) {
                if (expectedAddress == null) ctl.submit(action) else ctl.submitForCamera(action, expectedAddress)
            }
        }
    }
}

