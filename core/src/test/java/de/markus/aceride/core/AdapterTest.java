package de.markus.aceride.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static de.markus.aceride.core.CameraAdapter.*;

/** Exercises the V2 policy boundary, not a pretend vendor SDK or Bluetooth stack. */
final class AdapterTest {
    private static class Api implements Insta360V2Adapter.V2Api {
        boolean authorized = true, advertise = true, supports = true;
        String model = "ACE_PRO_1";
        int registered, unregistered, started, stopped, loaded, released, disconnected, shutdowns, queries;
        CompletableFuture<Void> shutdownReply;
        Consumer<Status> status;
        Consumer<String> drop;
        @Override public CompletableFuture<Void> connectBle(String a) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Boolean> checkAuthorization() { return CompletableFuture.completedFuture(authorized); }
        @Override public CompletableFuture<String> fetchExactModel() { return CompletableFuture.completedFuture(model); }
        @Override public CompletableFuture<Void> loadCaptureJson() { loaded++; return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Status> readFreshCaptureAndMode() {
            queries++;
            return CompletableFuture.completedFuture(new Status(Recording.IDLE, Mode.VIDEO, 90, 0L, 1));
        }
        @Override public CompletableFuture<Void> bleWakeUp(String m, String n) { return CompletableFuture.completedFuture(null); }
        @Override public boolean bleSupportsOriginalAcePro() { return supports; }
        @Override public boolean advertiserAvailable() { return advertise; }
        @Override public CompletableFuture<Void> startCapture() { started++; return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> stopCapture() { stopped++; return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> shutdown() {
            shutdowns++; return shutdownReply == null ? CompletableFuture.completedFuture(null) : shutdownReply;
        }
        @Override public AutoCloseable register(Consumer<Status> s, Consumer<String> d) {
            registered++; status=s; drop=d; return () -> unregistered++;
        }
        @Override public void disconnectBle() { disconnected++; }
        @Override public void close() { released++; }
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    static int run() {
        Camera camera = new Camera("AA", "Ace Pro TEST", "ACE_PRO_1");
        int n = 0;
        Api api = new Api();
        try (Insta360V2Adapter adapter = new Insta360V2Adapter(api)) {
            api.authorized=false;
            check(adapter.connect(camera).isCompletedExceptionally(), "unauthorized connect");
            check(adapter.startVideo().isCompletedExceptionally() && api.started==0, "no unauthorized command");
            System.out.println("PASS SDK-Grenze: fehlende Autorisierung sperrt Aufnahme"); n++;
        }
        api=new Api();
        try (Insta360V2Adapter adapter = new Insta360V2Adapter(api)) {
            api.model="ACE_PRO_2";
            check(adapter.connect(camera).isCompletedExceptionally() && api.loaded==0, "reject wrong model");
            System.out.println("PASS SDK-Grenze: Ace-Pro-2-Modell wird abgewiesen"); n++;
        }
        api=new Api();
        try (Insta360V2Adapter adapter = new Insta360V2Adapter(api)) {
            api.advertise=false;
            check(!adapter.supportsWake(camera) && adapter.wakeSignal(camera).isCompletedExceptionally(), "advertise unavailable");
            adapter.connect(camera).join(); adapter.startVideo().join();
            check(api.started==1, "powered-on camera still supported");
            System.out.println("PASS SDK-Grenze: fehlendes Advertising verhindert nur Wake"); n++;
        }
        api=new Api();
        final int[] statuses={0};
        try (Insta360V2Adapter adapter = new Insta360V2Adapter(api)) {
            adapter.setListener(new Listener() {
                @Override public void status(Status status) { statuses[0]++; }
                @Override public void disconnected(String reason) {}
            });
            adapter.connect(camera).join();
            Consumer<Status> old = api.status;
            adapter.disconnect(); adapter.connect(camera).join();
            old.accept(new Status(Recording.RECORDING,Mode.VIDEO,90,1L,1));
            check(statuses[0]==0, "old generation event");
            api.status.accept(new Status(Recording.IDLE,Mode.VIDEO,90,0L,2));
            check(statuses[0]==1, "new generation event");
            System.out.println("PASS SDK-Grenze: alte Listener ändern neue Sitzung nicht"); n++;
        }
        check(api.registered == api.unregistered && api.released == 1, "listener release");
        System.out.println("PASS SDK-Grenze: Listener und SDK werden freigegeben"); n++;
        api = new Api();
        final int[] offStatuses = {0};
        try (Insta360V2Adapter adapter = new Insta360V2Adapter(api)) {
            adapter.setListener(new Listener() {
                @Override public void status(Status status) { offStatuses[0]++; }
                @Override public void disconnected(String reason) {}
            });
            adapter.connect(camera).join();
            Consumer<Status> old = api.status;
            api.shutdownReply = new CompletableFuture<>();
            CompletableFuture<Void> request = adapter.powerOff();
            check(adapter.freshStatus().isCompletedExceptionally() && api.queries == 0, "reads suspended before shutdown reply");
            old.accept(new Status(Recording.IDLE, Mode.VIDEO, 90, 0L, 1));
            check(offStatuses[0] == 0 && api.registered == api.unregistered, "event quiescence before send completes");
            int releases = api.disconnected;
            request.complete(null);
            check(api.disconnected == releases + 1, "complete releases BLE");
            adapter.connect(camera).join(); adapter.freshStatus().join();
            check(api.queries == 1, "new explicit session works");
            System.out.println("PASS SDK-Grenze: Aus sperrt Abfragen und Listener, gibt BLE frei und erlaubt neues Verbinden"); n++;
        }
        for (boolean cancel : new boolean[]{false, true}) {
            api = new Api();
            try (Insta360V2Adapter adapter = new Insta360V2Adapter(api)) {
                adapter.connect(camera).join(); api.shutdownReply = new CompletableFuture<>();
                CompletableFuture<Void> request = adapter.powerOff();
                int releases = api.disconnected;
                if (cancel) request.cancel(true); else request.completeExceptionally(new IllegalStateException("uncertain shutdown"));
                check(api.disconnected == releases + 1 && adapter.freshStatus().isCompletedExceptionally(), "uncertain request releases BLE");
            }
        }
        System.out.println("PASS SDK-Grenze: Fehler und Abbruch des Aus-Befehls geben BLE ebenfalls frei"); n++;
        return n;
    }
}
