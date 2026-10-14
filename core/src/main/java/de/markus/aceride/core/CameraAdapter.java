package de.markus.aceride.core;

import java.util.concurrent.CompletableFuture;

/** All operations are asynchronous, bounded by Controller. Never infer capture from ACK. */
public interface CameraAdapter extends AutoCloseable {
    record Camera(String address, String bleName, String model) {
        public Camera {
            if (address == null || address.trim().isEmpty() || bleName == null || bleName.trim().isEmpty())
                throw new IllegalArgumentException("Kameraadresse und BLE-Name fehlen.");
            if (!CameraModels.supported(model))
                throw new IllegalArgumentException("Unterstützt werden die ursprüngliche Ace Pro und GO 3S.");
        }
    }
    enum Recording { UNKNOWN, IDLE, RECORDING, TRANSITION }
    enum Mode { VIDEO, OTHER, UNKNOWN }
    enum StorageState { UNKNOWN, AVAILABLE, FULL, NO_CARD, READ_ONLY, ERROR }
    record Storage(StorageState state, Long freeBytes, Long totalBytes, long readAtMs) {
        public Storage {
            if (state == null) state = StorageState.UNKNOWN;
            if (freeBytes == null || totalBytes == null || freeBytes < 0 || totalBytes <= 0 || freeBytes > totalBytes) {
                freeBytes = null; totalBytes = null;
                if (state == StorageState.AVAILABLE) state = StorageState.UNKNOWN;
            }
        }
        public static Storage unknown() { return new Storage(StorageState.UNKNOWN, null, null, -1); }
    }
    /** readStartedAtMs is monotonic. A poll started before a command cannot confirm it. */
    record Status(Recording recording, Mode mode, Integer battery, Long seconds,
                  long readStartedAtMs, Storage storage, long batteryReadAtMs, String telemetryInfo) {
        public Status(Recording recording, Mode mode, Integer battery, Long seconds, long readStartedAtMs) {
            this(recording, mode, battery, seconds, readStartedAtMs, Storage.unknown(), readStartedAtMs, "");
        }
        public Status {
            if (recording == null || mode == null) throw new IllegalArgumentException("Status fehlt");
            if (battery != null && (battery < 0 || battery > 100)) battery = null;
            if (seconds != null && seconds < 0) seconds = null;
            if (storage == null) storage = Storage.unknown();
            if (telemetryInfo == null) telemetryInfo = "";
        }
    }
    interface Listener {
        void status(Status status);
        void disconnected(String reason);
    }
    void setListener(Listener listener);
    CompletableFuture<Void> connect(Camera camera);
    /** Success means broadcast/callback only; Controller must connect and query afterwards. */
    CompletableFuture<Void> wakeSignal(Camera camera);
    boolean supportsWake(Camera camera);
    /** GO 3S uses authorized BLE connect, not the ACE advertising wake command. */
    default boolean wakesOnConnect(Camera camera) { return false; }
    CompletableFuture<Status> freshStatus();
    CompletableFuture<Void> startVideo();
    CompletableFuture<Void> stopVideo();
    /** One-way request: completion is NOT proof that the camera is powered off. */
    CompletableFuture<Void> powerOff();
    void disconnect();
    @Override void close();
}

