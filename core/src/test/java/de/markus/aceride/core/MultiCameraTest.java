package de.markus.aceride.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static de.markus.aceride.core.CameraAdapter.*;
import static de.markus.aceride.core.Controller.Action.*;

/** Actual production profiles, adapter and controller, with two independent simulated devices. */
final class MultiCameraTest {
    private static int passed;
    private static final Camera ACE = new Camera("AA", "Ace Pro TESTA", CameraModels.ACE);
    private static final Camera GO = new Camera("BB", "GO 3S TESTB", CameraModels.GO);
    private static final Controller.Timing FAST = new Controller.Timing(200, 1000, 40, 100, 10000, 1000);
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    @FunctionalInterface interface Case { void run() throws Exception; }
    private static void test(String name, Case body) throws Exception { body.run(); passed++; System.out.println("PASS Mehrkamera: " + name); }
    private static void rejects(Runnable body) { boolean rejected = false; try { body.run(); } catch (RuntimeException expected) { rejected = true; } check(rejected, "must reject"); }
    private static class Api implements Insta360V2Adapter.V2Api {
        final AtomicLong clock;
        String address, modelOverride;
        boolean authorized = true, queryFails;
        int connections, broadcasts, shutdowns, loaded, released, started, stopped;
        final Map<String,Recording> recordings = new HashMap<>(Map.of("AA", Recording.IDLE, "BB", Recording.IDLE));
        final Map<String,Integer> clips = new HashMap<>();
        Consumer<Status> callback; Consumer<String> drop;
        volatile CompletableFuture<Status> pendingQuery;
        volatile boolean blockRelease;
        final CountDownLatch releaseEntered = new CountDownLatch(1), releaseGate = new CountDownLatch(1);
        Api(AtomicLong clock) { this.clock = clock; }
        Status frame() {
            boolean go = "BB".equals(address);
            long now = clock.get();
            return new Status(recordings.get(address), Mode.VIDEO, go ? 43 : 81,
                    recordings.get(address) == Recording.RECORDING ? 7L : 0L, now,
                    new Storage(StorageState.AVAILABLE, go ? 16L : 80L, 100L, now), now, "simulated " + address);
        }
        public CompletableFuture<Void> connectBle(String address) { this.address = address; connections++; return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Boolean> checkAuthorization() { return CompletableFuture.completedFuture(authorized); }
        public CompletableFuture<String> fetchExactModel() { return CompletableFuture.completedFuture(modelOverride != null ? modelOverride : "BB".equals(address) ? CameraModels.GO : CameraModels.ACE); }
        public CompletableFuture<Void> loadCaptureJson() { loaded++; return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Status> readFreshCaptureAndMode() {
            if (pendingQuery != null) { var value = pendingQuery; pendingQuery = null; return value; }
            return queryFails ? CompletableFuture.failedFuture(new IllegalStateException("no camera reply")) : CompletableFuture.completedFuture(frame());
        }
        public CompletableFuture<Void> bleWakeUp(String model,String name) { broadcasts++; return CompletableFuture.completedFuture(null); }
        public boolean bleSupportsOriginalAcePro() { return true; }
        public boolean advertiserAvailable() { return false; }
        public CompletableFuture<Void> startCapture() { started++; recordings.put(address, Recording.RECORDING); clips.merge(address,1,Integer::sum); return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> stopCapture() { stopped++; recordings.put(address, Recording.IDLE); return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> shutdown() { shutdowns++; return CompletableFuture.completedFuture(null); }
        public AutoCloseable register(Consumer<Status> callback, Consumer<String> drop) { this.callback=callback; this.drop=drop; return () -> released++; }
        public void disconnectBle() {
            if (blockRelease) {
                releaseEntered.countDown();
                try { if (!releaseGate.await(2,TimeUnit.SECONDS)) throw new IllegalStateException("release timed out"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }
        }
        public void close() {}
    }
    private static class Fixture implements AutoCloseable {
        final AtomicLong now = new AtomicLong(100);
        CameraProfiles profiles = CameraProfiles.empty().select(GO).select(ACE);
        final Api api = new Api(now);
        final Controller ctl;
        Fixture() {
            ctl = new Controller(new Insta360V2Adapter(api), now::get, FAST, false, this::savePause);
            ctl.restore(ACE);
        }
        void savePause(boolean pause) { profiles = profiles.pause(ctl.selectedCamera().address(), pause); }
        Controller.State act(Controller.Action action) throws Exception { now.addAndGet(10); return ctl.submit(action).get(2,TimeUnit.SECONDS); }
        void select(Camera camera) { ctl.switchCamera(camera, profiles.get(camera.address()).pause(), () -> profiles=profiles.select(camera)); }
        public void close() { ctl.close(); }
    }
    static int run() throws Exception {
        test("Suchnamen unterscheiden GO 3S, GO 3, Ace Pro und Ace Pro 2", () -> {
            check(CameraModels.GO.equals(CameraModels.discover("Insta360 GO 3S ABCDEF")), "GO3S");
            check(CameraModels.ACE.equals(CameraModels.discover("Ace Pro ABCDEF")), "Ace");
            check(CameraModels.discover("GO 3 ABCDEF") == null && CameraModels.discover("Ace Pro 2 ABCDEF") == null && CameraModels.discover("GoPro") == null, "other models rejected");
            rejects(() -> new Camera("CC", "GO 3", "GO_3"));
        });
        test("1.0-Migration erhält Ace-Pro-Auswahl, Pause und passende Kennung", () -> {
            var p=CameraProfiles.migrate(ACE,true,"AA","TESTAA");
            check(p.selected().camera().equals(ACE) && p.selected().pause() && p.selected().wakeId().equals("TESTAA"),"migration");
        });
        test("Migration verwirft fremde oder ungültige Aufweckkennung", () -> {
            check(CameraProfiles.migrate(ACE,false,"BB","TESTBB").selected().wakeId()==null,"address mismatch");
            check(CameraProfiles.migrate(ACE,false,"AA","?").selected().wakeId()==null,"invalid wake");
        });
        test("Zweites Profil erhält bisherige Kamera und ihre Pausenabsicht", () -> {
            var p=CameraProfiles.empty().select(ACE).pause("AA",true).wake("AA","SERIALTESTAA").select(GO);
            check(p.all().size()==2 && !p.selected().pause() && p.selected().wakeId()==null && p.get("AA").pause(),"separate data");
        });
        test("Erneute Auswahl dupliziert keine Kamera und behält ihre Kennung", () -> {
            var p=CameraProfiles.empty().select(ACE).wake("AA","TESTAA").select(GO).select(ACE);
            check(p.all().size()==2 && p.selected().wakeId().equals("TESTAA"),"upsert retains wake");
        });
        test("Profilformat bewahrt Sonderzeichen und aktive Auswahl nach Neustart", () -> {
            Camera odd = new Camera("ÄA", "GO 3S |\nü Test", CameraModels.GO);
            var p=CameraProfiles.empty().select(ACE).select(odd).pause("ÄA",true);
            var restored=CameraProfiles.decode(p.encode());
            check(restored.selected().camera().equals(odd) && restored.selected().pause() && restored.all().size()==2,"roundtrip");
        });
        test("Beschädigte Profile, doppelte Adressen und Modellwechsel werden abgewiesen", () -> {
            rejects(() -> CameraProfiles.decode("2\n"));
            rejects(() -> CameraProfiles.decode("1\n\nx|y|GO_3S|maybe|"));
            rejects(() -> new CameraProfiles(List.of(new CameraProfiles.Profile(ACE,false,null),new CameraProfiles.Profile(ACE,false,null)),"AA"));
            rejects(() -> CameraProfiles.empty().select(ACE).select(new Camera("AA","Other",CameraModels.GO)));
        });
        test("GO 3S erhält niemals eine Ace-Pro-Broadcastkennung", () -> rejects(() -> CameraProfiles.empty().select(GO).wake("BB","TESTBB")));
        test("Vergessen entfernt nur ausgewähltes Profil und aktiviert keine andere Kamera", () -> {
            var p=CameraProfiles.empty().select(ACE).select(GO).forgetSelected();
            check(p.selected()==null && p.all().size()==1 && p.get("AA")!=null,"retains Ace");
        });
        test("Leere Profile bleiben nach Neustart leer, keine Legacy-Reaktivierung", () -> check(CameraProfiles.decode(CameraProfiles.empty().encode()).selected()==null,"empty roundtrip"));
        test("GO-3S-Erstverbindung benötigt Autorisierung und echtes Modell", () -> {
            Api api=new Api(new AtomicLong(100)); api.authorized=false;
            try (var adapter=new Insta360V2Adapter(api)) {
                check(adapter.connect(GO).isCompletedExceptionally() && adapter.startVideo().isCompletedExceptionally() && api.loaded==0 && api.started==0,"auth gate");
            }
        });
        test("Ausgewählte GO 3S akzeptiert weder GO 3 noch Ace Pro 2", () -> {
            for (String model : List.of("GO_3","ACE_PRO_2",CameraModels.ACE)) {
                Api api=new Api(new AtomicLong(100)); api.modelOverride=model;
                try (var adapter=new Insta360V2Adapter(api)) { check(adapter.connect(GO).isCompletedExceptionally() && api.loaded==0 && api.started==0,"exact model gate"); }
            }
        });
        test("Ausgewählte Ace Pro akzeptiert keine GO 3S", () -> {
            Api api=new Api(new AtomicLong(100)); api.modelOverride=CameraModels.GO;
            try (var adapter=new Insta360V2Adapter(api)) { check(adapter.connect(ACE).isCompletedExceptionally() && api.loaded==0,"cross model gate"); }
        });
        test("GO 3S verwendet autorisierten Connect statt Advertising", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO); var s=f.act(WAKE);
                check(s.connection()==Controller.Connection.CONNECTED && s.recording()==Recording.IDLE && f.api.broadcasts==0 && f.api.connections==1 && f.api.started==0,"connect wake");
            }
        });
        test("Erfolgreicher GO-Connect ohne Statusantwort gilt nicht als bereit", () -> {
            try (Fixture f=new Fixture()) { f.select(GO);f.api.queryFails=true;check(f.act(WAKE).recording()==Recording.UNKNOWN && f.api.started==0,"no wake proof from connect alone"); }
        });
        test("GO 3S kann Start Pause Fortsetzen Stopp mit zwei neuen Clips", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO);f.act(START);f.act(PAUSE);check(f.ctl.state().pauseIntent(),"pause saved");
                f.act(RESUME);f.act(STOP);
                check(f.api.clips.get("BB")==2 && f.api.clips.get("AA")==null && !f.ctl.state().pauseIntent(),"only GO clips");
            }
        });
        test("Wechsel sendet keine Aufnahmebefehle und zeigt keine alten Mengenwerte", () -> {
            try (Fixture f=new Fixture()) {
                f.act(CONNECT); check(f.ctl.state().battery()==81,"Ace battery"); int count=f.api.connections;
                f.select(GO);var s=f.ctl.state();
                check(s.camera().equals(GO) && s.recording()==Recording.UNKNOWN && s.lastConfirmed()==Recording.UNKNOWN && s.battery()==null && s.seconds()==null && s.storage().state()==StorageState.UNKNOWN,"fresh identity");
                check(f.api.connections==count && f.api.started==0 && f.api.stopped==0,"switch does not connect/capture");
                f.act(CONNECT);check(f.ctl.state().battery()==43 && f.ctl.state().storage().freeBytes()==16,"GO values only");
            }
        });
        test("Rückwechsel erhält Ace-Pro-Pause und verbindet ohne Fortsetzen", () -> {
            try (Fixture f=new Fixture()) {
                f.act(START);f.act(PAUSE);f.select(GO);f.act(CONNECT);f.select(ACE);
                check(f.ctl.state().pauseIntent(),"Ace pause restored");f.act(CONNECT);
                check(f.ctl.state().recording()==Recording.IDLE && f.api.started==1,"no auto resume");
                f.act(RESUME);check(f.api.clips.get("AA")==2,"explicit resume new Ace clip");
            }
        });
        test("GO-Pause bleibt beim Stoppen der Ace Pro unverändert", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO);f.act(START);f.act(PAUSE);f.select(ACE);f.act(CONNECT);f.act(STOP);
                check(f.profiles.get("BB").pause() && !f.profiles.get("AA").pause(),"per camera pause");
            }
        });
        test("Wechsel während Aufnahme wird ohne Trennen oder Speichern abgewiesen", () -> {
            try (Fixture f=new Fixture()) {
                f.act(START);int released=f.api.released;rejects(() -> f.select(GO));
                check(f.ctl.selectedCamera().equals(ACE) && f.profiles.selected().camera().equals(ACE) && f.api.released==released,"recording preserved");
            }
        });
        test("Speicherfehler erhält Auswahl und laufende Kameraverbindung", () -> {
            try (Fixture f=new Fixture()) {
                f.act(CONNECT);int released=f.api.released;
                rejects(() -> f.ctl.switchCamera(GO,false, () -> {throw new IllegalStateException("disk full");}));
                check(f.ctl.selectedCamera().equals(ACE) && f.ctl.state().connection()==Controller.Connection.CONNECTED && f.api.released==released,"transactional selection");
            }
        });
        test("Alte Status- und Disconnect-Listener beeinflussen neue Kamera nicht", () -> {
            try (Fixture f=new Fixture()) {
                f.act(CONNECT);var old=f.api.callback;var oldDrop=f.api.drop;
                f.select(GO);f.act(CONNECT);
                old.accept(new Status(Recording.RECORDING,Mode.VIDEO,99,99L,f.now.get()+1));oldDrop.accept("old link");
                check(f.ctl.state().camera().equals(GO) && f.ctl.state().recording()==Recording.IDLE && f.ctl.state().battery()==43 && f.ctl.state().connection()==Controller.Connection.CONNECTED,"listener generation fence");
            }
        });
        test("Veraltete Start-Taste für Ace Pro kann GO 3S nicht starten", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO);f.act(CONNECT);
                check(f.ctl.submitForCamera(START,"AA").isCompletedExceptionally() && f.api.started==0,"old PI dropped");
                f.ctl.submitForCamera(START,"BB").get(2,TimeUnit.SECONDS);check(f.api.started==1,"current button works");
            }
        });
        test("GO-Ausschalten ist in UI und Controller gesperrt, auch während REC", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO);f.act(START);
                check(!RideControls.enabled(RideControls.Slot.POWER_OFF,f.ctl.state(),true,true),"disabled UI");
                f.act(POWER_OFF);check(f.api.shutdowns==0 && f.api.stopped==0 && f.ctl.state().recording()==Recording.RECORDING && f.ctl.state().power()==Controller.Power.ON,"no false off or stop");
            }
        });
        test("SDK-Adapter verweigert GO-Shutdown und behält Statuszugriff", () -> {
            Api api=new Api(new AtomicLong(100));
            try (var adapter=new Insta360V2Adapter(api)) {
                adapter.connect(GO).join();check(adapter.powerOff().isCompletedExceptionally() && api.shutdowns==0,"off unsupported");
                adapter.freshStatus().join();check(api.released==0,"still connected");
            }
        });
        test("Physischer GO-Auslöser gleicht Status ab und hebt lokale Pause auf", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO);f.act(START);f.act(PAUSE);f.api.recordings.put("BB",Recording.RECORDING);f.now.addAndGet(100);
                f.api.callback.accept(f.api.frame());check(f.ctl.state().recording()==Recording.RECORDING && !f.profiles.get("BB").pause(),"physical shutter wins");
            }
        });
        test("GO-Funkabbruch erzeugt keine aufweckende Wiederverbindungsschleife", () -> {
            try (Fixture f=new Fixture()) {
                f.select(GO);f.act(START);int connections=f.api.connections;f.api.drop.accept("lost GO");
                Thread.sleep(25);
                check(f.ctl.state().recording()==Recording.UNKNOWN && f.ctl.state().lastConfirmed()==Recording.RECORDING && f.api.connections==connections && f.api.broadcasts==0,"no unsolicited connect");
                check(!f.ctl.reconnectPending(),"no wake capable auto reconnect queued");
                f.act(CONNECT);check(f.ctl.state().recording()==Recording.RECORDING && f.api.started==1,"reconcile without new clip");
            }
        });
        test("Neustart mit gespeicherter GO-Pause beginnt keine Aufnahme", () -> {
            AtomicLong clock=new AtomicLong(100);Api api=new Api(clock);
            var profiles=CameraProfiles.decode(CameraProfiles.empty().select(GO).pause("BB",true).encode());
            try (var ctl=new Controller(new Insta360V2Adapter(api),clock::get,FAST,profiles.selected().pause(),p -> {})) {
                ctl.restore(profiles.selected().camera());check(api.connections==0 && api.started==0,"restore silent");
                ctl.submit(CONNECT).get(2,TimeUnit.SECONDS);check(ctl.state().pauseIntent() && api.started==0,"connect no resume");
            }
        });
        test("Wechsel während eines Aufnahmevorgangs lässt Befehl und Auswahl bestehen", () -> {
            try (Fixture f=new Fixture()) {
                f.act(CONNECT);CompletableFuture<Status> gate=new CompletableFuture<>();f.api.pendingQuery=gate;
                var action=f.ctl.submit(START);
                check(f.ctl.state().busy(),"command busy");rejects(() -> f.select(GO));
                check(f.profiles.selected().camera().equals(ACE) && !action.isDone(),"selection untouched");
                // Wait until the serialized worker owns the pending read.
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
                while (f.api.pendingQuery!=null && System.nanoTime()<until) Thread.sleep(2);
                gate.complete(f.api.frame());action.get(2,TimeUnit.SECONDS);
                check(f.api.started==1 && f.ctl.selectedCamera().equals(ACE),"original command completes once");
            }
        });
        test("Wechsel unterbricht Hintergrundlesen, alte Antwort wird verworfen", () -> {
            AtomicLong now=new AtomicLong(100);Api api=new Api(now);
            try (var ctl=new Controller(new Insta360V2Adapter(api),now::get,new Controller.Timing(200,1000,40,100,20,1000),false,p -> {})) {
                ctl.restore(ACE);ctl.submit(CONNECT).get(2,TimeUnit.SECONDS);
                CompletableFuture<Status> gate=new CompletableFuture<>();api.pendingQuery=gate;
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
                while (!(ctl.state().busy() && ctl.state().background() && api.pendingQuery == null) && System.nanoTime()<until) Thread.sleep(2);
                check(ctl.state().background() && api.pendingQuery == null,"background read owns pending query");
                ctl.switchCamera(GO,false, () -> {});
                check(gate.isCancelled() || !gate.isDone(),"old pending response cannot apply");
                ctl.submit(CONNECT).get(2,TimeUnit.SECONDS);
                gate.complete(new Status(Recording.RECORDING,Mode.VIDEO,99,9L,now.get()));
                check(ctl.state().camera().equals(GO) && ctl.state().recording()==Recording.IDLE && ctl.state().battery()==43 && api.started==0,"new state preserved");
            }
        });
        test("Langsame SDK-Freigabe blockiert weder Anzeige noch Befehlsabweisung", () -> {
            try (Fixture f=new Fixture()) {
                f.act(CONNECT);f.api.blockRelease=true;
                var switched=new CompletableFuture<Void>();
                Thread worker=new Thread(() -> {try {f.select(GO);switched.complete(null);} catch (Throwable ex) {switched.completeExceptionally(ex);} });
                worker.setDaemon(true);worker.start();
                try {
                    check(f.api.releaseEntered.await(1,TimeUnit.SECONDS),"release entered");
                    var snapshot=CompletableFuture.supplyAsync(f.ctl::state).get(500,TimeUnit.MILLISECONDS);
                    check(snapshot.busy() && snapshot.connection()==Controller.Connection.DISCONNECTED,"controller remains readable");
                    check(f.ctl.submitForCamera(START,"AA").isCompletedExceptionally() && f.api.started==0,"busy switch rejects start");
                } finally {f.api.blockRelease=false;f.api.releaseGate.countDown();}
                switched.get(1,TimeUnit.SECONDS);check(f.ctl.selectedCamera().equals(GO) && !f.ctl.state().busy(),"switch completes");
            }
        });
        return passed;
    }
}
