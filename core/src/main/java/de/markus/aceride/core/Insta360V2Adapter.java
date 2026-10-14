package de.markus.aceride.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Transport policy for the documented Camera SDK V2.1.5. V2Api isolates the actual
 * SDK's package names/native ABI. Its production binding requires the authorized Demo.
 * This class is real adapter logic, but alone is NOT an SDK implementation.
 */
public final class Insta360V2Adapter implements CameraAdapter {
    public interface V2Api extends AutoCloseable {
        // Required mapping: CameraDevice.get(ConnectType.BLE), connectBle(system device).
        CompletableFuture<Void> connectBle(String address);
        CompletableFuture<Boolean> checkAuthorization();
        CompletableFuture<String> fetchExactModel();
        CompletableFuture<Void> loadCaptureJson();
        // Must query the camera; getValue() and a cached Boolean are insufficient.
        CompletableFuture<Status> readFreshCaptureAndMode();
        // Required mapping: CameraDevice.bleWakeUp(exact ACE_PRO enum, persisted BLE name).
        CompletableFuture<Void> bleWakeUp(String exactModel, String bleName);
        boolean bleSupportsOriginalAcePro();
        boolean advertiserAvailable();
        CompletableFuture<Void> startCapture();
        CompletableFuture<Void> stopCapture();
        CompletableFuture<Void> shutdown();
        /** Listener pair must be removed by this handle. Only verified data are forwarded. */
        AutoCloseable register(Consumer<Status> status, Consumer<String> disconnect);
        void disconnectBle();
        @Override void close();
    }
    private final V2Api sdk;
    private volatile Listener listener;
    private volatile boolean ready;
    private Camera selected;
    private long generation;
    private AutoCloseable subscription;
    public Insta360V2Adapter(V2Api sdk) { this.sdk = sdk; }
    @Override public void setListener(Listener value) { listener = value; }
    @Override public synchronized CompletableFuture<Void> connect(Camera camera) {
        disconnect();
        selected = camera;
        long thisGeneration = ++generation;
        subscription = sdk.register(s -> {
            synchronized (Insta360V2Adapter.this) {
                if (generation == thisGeneration && ready && listener != null) listener.status(s);
            }
        }, reason -> {
            synchronized (Insta360V2Adapter.this) {
                if (generation != thisGeneration) return;
                ready = false;
                if (listener != null) listener.disconnected(reason);
            }
        });
        return sdk.connectBle(camera.address())
                .thenCompose(v -> sdk.checkAuthorization())
                .thenCompose(authorized -> authorized ? sdk.fetchExactModel()
                        : CompletableFuture.failedFuture(new IllegalStateException(
                        "Kamera nicht autorisiert. Bestätigung auf dem Kameradisplay prüfen und erneut verbinden.")))
                .thenCompose(model -> camera.model().equals(model) ? sdk.loadCaptureJson()
                        : CompletableFuture.failedFuture(new IllegalStateException(
                        "Modellprüfung fehlgeschlagen: erwartet wird " + CameraModels.label(camera.model()) + ".")))
                .thenAccept(v -> {
                    synchronized (Insta360V2Adapter.this) {
                        if (generation != thisGeneration) throw new IllegalStateException("Veraltete Verbindung");
                        ready = true;
                    }
                });
    }
    @Override public boolean supportsWake(Camera camera) {
        return wakesOnConnect(camera) || CameraModels.ACE.equals(camera.model()) && sdk.bleSupportsOriginalAcePro() && sdk.advertiserAvailable();
    }
    @Override public boolean wakesOnConnect(Camera camera) { return CameraModels.GO.equals(camera.model()); }
    @Override public CompletableFuture<Void> wakeSignal(Camera camera) {
        if (wakesOnConnect(camera)) return CompletableFuture.failedFuture(new IllegalStateException(
                "GO 3S wird durch eine ausdrücklich angeforderte autorisierte Verbindung aufgeweckt."));
        if (!supportsWake(camera)) return CompletableFuture.failedFuture(new IllegalStateException(
                "SDK-Modellunterstützung oder Bluetooth-Advertising fehlt. Kamera manuell einschalten."));
        return sdk.bleWakeUp(camera.model(), camera.bleName());
    }
    @Override public CompletableFuture<Status> freshStatus() {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Keine autorisierte Kameraverbindung"));
        return sdk.readFreshCaptureAndMode();
    }
    @Override public CompletableFuture<Void> startVideo() {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Kamera nicht bereit"));
        // Controller has already fetched the current video mode. Never change settings.
        return sdk.startCapture();
    }
    @Override public CompletableFuture<Void> stopVideo() {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Kamera nicht bereit"));
        return sdk.stopCapture();
    }
    @Override public synchronized CompletableFuture<Void> powerOff() {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Kamera nicht bereit"));
        if (selected == null || !CameraModels.shutdown(selected.model()))
            return CompletableFuture.failedFuture(new IllegalStateException("GO 3S bitte an der Kamera ausschalten. SDK 2.1.5 unterstützt diesen Befehl nicht."));
        // Quiesce events/reads before sending the one-way shutdown. Reopening
        // later goes through the normal explicit connect/wake authorization path.
        ready = false; generation++;
        if (subscription != null) {
            try { subscription.close(); } catch (Exception ignored) {}
            subscription = null;
        }
        try {
            CompletableFuture<Void> request = sdk.shutdown();
            request.whenComplete((v,e) -> disconnect());
            return request;
        } catch (RuntimeException failure) {
            disconnect(); return CompletableFuture.failedFuture(failure);
        }
    }
    @Override public synchronized void disconnect() {
        ready = false; generation++;
        if (subscription != null) {
            try { subscription.close(); } catch (Exception ignored) {}
            subscription = null;
        }
        sdk.disconnectBle();
    }
    @Override public void close() { disconnect(); listener = null; sdk.close(); }
}

