package de.markus.aceride.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Test sources only; this class is never included in the Android application. */
public final class SimulatedCamera implements CameraAdapter {
    public Listener listener;
    public Recording recording = Recording.IDLE;
    public Mode mode = Mode.VIDEO;
    public volatile int starts, stops, connects, wakes, clipFiles, powerOffs, queries, disconnects;
    public boolean connected, powered = true, wakeAvailable = true, staleQueries, ignoreCommands;
    public boolean loseStartAck, queryFails, ignorePowerOff, losePowerOffAck;
    public CompletableFuture<Void> nextStart;
    public volatile CompletableFuture<Void> nextConnect;
    public volatile CompletableFuture<Status> nextQuery;
    public Integer reportedBattery = 76;
    public Long reportedSeconds = 5L, reportedBatteryAt;
    public Storage reportedStorage = Storage.unknown();
    private final LongSupplier clock;
    public SimulatedCamera(LongSupplier clock) { this.clock = clock; }
    @Override public void setListener(Listener listener) { this.listener = listener; }
    @Override public CompletableFuture<Void> connect(Camera camera) {
        connects++;
        if (nextConnect != null) {
            CompletableFuture<Void> result = nextConnect; nextConnect = null;
            result.whenComplete((v,e) -> { if (e == null) connected = true; });
            return result;
        }
        if (!powered) return CompletableFuture.failedFuture(new IllegalStateException("Kamera ausgeschaltet"));
        connected = true; return CompletableFuture.completedFuture(null);
    }
    @Override public CompletableFuture<Void> wakeSignal(Camera camera) {
        wakes++; powered = true; return CompletableFuture.completedFuture(null);
    }
    @Override public boolean supportsWake(Camera camera) { return wakeAvailable; }
    @Override public CompletableFuture<Status> freshStatus() {
        queries++;
        if (nextQuery != null) { CompletableFuture<Status> result = nextQuery; nextQuery = null; return result; }
        if (queryFails || !connected || !powered) return CompletableFuture.failedFuture(new IllegalStateException("Status fehlt"));
        long at = staleQueries ? -10000 : clock.getAsLong();
        return CompletableFuture.completedFuture(new Status(recording, mode, reportedBattery,
                recording == Recording.RECORDING ? reportedSeconds : Long.valueOf(0), at, reportedStorage,
                reportedBatteryAt == null ? at : reportedBatteryAt, "simulierte Mengenwerte"));
    }
    @Override public CompletableFuture<Void> startVideo() {
        starts++;
        if (!ignoreCommands && recording != Recording.RECORDING) { recording = Recording.RECORDING; clipFiles++; }
        if (nextStart != null) return nextStart;
        if (loseStartAck) return new CompletableFuture<>();
        return CompletableFuture.completedFuture(null);
    }
    @Override public CompletableFuture<Void> stopVideo() {
        stops++; if (!ignoreCommands) recording = Recording.IDLE;
        return CompletableFuture.completedFuture(null);
    }
    @Override public CompletableFuture<Void> powerOff() {
        powerOffs++;
        if (!ignorePowerOff) { powered = false; drop(); }
        return losePowerOffAck ? new CompletableFuture<>() : CompletableFuture.completedFuture(null);
    }
    public void physicalShutter(boolean record) {
        recording = record ? Recording.RECORDING : Recording.IDLE;
        if (connected && listener != null) listener.status(freshStatus().join());
    }
    public void drop() {
        connected = false;
        if (listener != null) listener.disconnected("simulierter Funkabbruch");
    }
    @Override public void disconnect() { disconnects++; connected = false; }
    @Override public void close() { disconnect(); listener = null; }
}

