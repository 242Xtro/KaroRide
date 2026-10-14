package de.markus.aceride.core;

/** Typed local diagnostics. No addresses, names, exception messages or raw SDK text. */
public interface Diagnostics {
    enum Event { APP_OPEN, SESSION_OPEN, SESSION_CLOSE, ACTION_REQUEST, ACTION_DROPPED,
        ACTION_END, ACTION_ERROR, STATUS_REQUEST, STATUS_REPLY, STATUS_ERROR, STATE,
        CONNECTION_LOST, RECONNECT_SCHEDULED, SDK_CONNECT_BEGIN, SDK_CONNECT_DONE,
        SDK_DISCONNECT_CALLBACK, SDK_SHUTDOWN_SEND, SDK_SHUTDOWN_SENT,
        WAKE_START, WAKE_ADVERTISE_STARTED, WAKE_STOP_REQUEST, WAKE_STOPPED, WAKE_STOP_ERROR,
        SDK_GATT_LOCAL_CLOSE, SDK_GATT_LOCAL_CLOSED, SDK_DEVICE_RELEASE, SDK_DEVICE_RELEASED, SDK_RELEASE_ERROR }
    void record(Event event, Enum<?>... values);
    default void status(CameraAdapter.Status status) {}
    default void state(Controller.State state) {}
    Diagnostics NONE = (event, values) -> {};
}
