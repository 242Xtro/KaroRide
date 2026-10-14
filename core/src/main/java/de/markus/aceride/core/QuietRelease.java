package de.markus.aceride.core;

/** Local GATT teardown precedes vendor release, which may send a disconnect frame. */
public final class QuietRelease {
    private QuietRelease() {}
    public interface Port {
        void stopAdvertising();
        void closeGatt();
        void releaseDevice();
    }
    public static void release(Port port, Diagnostics log) {
        try { port.stopAdvertising(); } catch (RuntimeException | LinkageError failure) { log.record(Diagnostics.Event.WAKE_STOP_ERROR); }
        try {
            log.record(Diagnostics.Event.SDK_GATT_LOCAL_CLOSE); port.closeGatt();
            log.record(Diagnostics.Event.SDK_GATT_LOCAL_CLOSED);
        } catch (RuntimeException | LinkageError failure) { log.record(Diagnostics.Event.SDK_RELEASE_ERROR); }
        try {
            log.record(Diagnostics.Event.SDK_DEVICE_RELEASE); port.releaseDevice();
            log.record(Diagnostics.Event.SDK_DEVICE_RELEASED);
        } catch (RuntimeException | LinkageError failure) { log.record(Diagnostics.Event.SDK_RELEASE_ERROR); }
    }
}
