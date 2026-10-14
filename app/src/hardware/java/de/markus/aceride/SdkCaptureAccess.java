package de.markus.aceride;

import com.arashivision.sdk.camera.api.CameraDevice;
import com.arashivision.sdk.camera.core.DeviceCore;
import com.arashivision.sdk.camera.service.CameraDeviceInternal;

/**
 * Version-pinned bridge to the SDK's exported JVM getter. Kotlin calls it internal.
 * It exposes fetchCaptureState(), the actual request/reply operation. The high-level
 * isWorking() reads a cache. No reflection, patched SDK or custom wire protocol.
 * SDK updates MUST revalidate this getter and request semantics.
 */
public final class SdkCaptureAccess {
    private SdkCaptureAccess() {}
    public static DeviceCore core(CameraDevice device) {
        if (!(device instanceof CameraDeviceInternal))
            throw new IllegalStateException("Frische Kamera-Statusabfrage nicht verfügbar.");
        return ((CameraDeviceInternal) device).getDeviceCore$verticalCamera_release();
    }
}
