package de.markus.aceride.core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import static de.markus.aceride.core.CameraAdapter.*;
import static de.markus.aceride.core.Controller.Action.*;

/** Dependency-free JVM test harness exercising the actual production Controller. */
public final class ControllerTest {
    private static int passed;
    private static final Camera CAMERA = new Camera("AA:BB:CC:DD:EE:FF", "Insta360 Ace Pro TEST", "ACE_PRO_1");
    private static final Controller.Timing FAST = new Controller.Timing(100, 30, 30, 100, 10000, 1000);
    private static class Fixture implements AutoCloseable {
        final AtomicLong now = new AtomicLong(100);
        final AtomicBoolean pause = new AtomicBoolean();
        final SimulatedCamera camera = new SimulatedCamera(now::get);
        final Controller ctl = new Controller(camera, now::get, FAST, false, pause::set);
        Fixture() { ctl.restore(CAMERA); }
        Controller.State act(Controller.Action action) throws Exception {
            now.addAndGet(10); return ctl.submit(action).get(2, TimeUnit.SECONDS);
        }
        @Override public void close() { ctl.close(); }
    }
    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    @FunctionalInterface interface Test { void run() throws Exception; }
    private static void test(String name, Test test) throws Exception {
        test.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        test("Erstverbindung bestätigt tatsächlichen Leerlauf", () -> {
            try (Fixture f = new Fixture()) { check(f.act(CONNECT).recording() == Recording.IDLE, "status"); }
        });
        test("Start Stopp Pause Fortsetzen erzeugen neue Clips", () -> {
            try (Fixture f = new Fixture()) {
                check(f.act(START).recording() == Recording.RECORDING, "start");
                check(f.act(PAUSE).pauseIntent(), "pause intent");
                check(f.ctl.state().recording() == Recording.IDLE, "pause confirmation");
                check(f.act(RESUME).recording() == Recording.RECORDING, "resume");
                check(f.camera.clipFiles == 2, "new file on resume");
                check(!f.act(STOP).pauseIntent(), "stop clears pause");
                f.act(RESUME); check(f.camera.starts == 2, "no resume after stop");
                f.act(START); check(f.camera.clipFiles == 3, "explicit new start");
            }
        });
        test("Aufwecksignal wird durch Verbindung und Status ergänzt", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.powered = false;
                check(f.act(WAKE).recording() == Recording.IDLE, "wake verified");
                check(f.camera.wakes == 1 && f.camera.connects == 1, "connect after wake");
            }
        });
        test("Start weckt nur bei fehlgeschlagener Verbindung automatisch", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.powered = false; f.act(START);
                check(f.camera.wakes == 1 && f.camera.starts == 1, "automatic wake");
            }
        });
        test("Aufwecken ohne Status ist kein Erfolg", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.queryFails = true;
                check(f.act(WAKE).recording() == Recording.UNKNOWN, "no fake wake success");
                check(f.camera.starts == 0, "no capture");
            }
        });
        test("Fehlendes Advertising erlaubt eingeschaltete Kamera", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.wakeAvailable = false; f.act(WAKE);
                check(f.camera.wakes == 0, "wake unsupported");
                check(f.act(START).recording() == Recording.RECORDING, "manual power control");
            }
        });
        test("Schnelle Mehrfachberührung wird verworfen", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.nextStart = new CompletableFuture<>();
                CompletableFuture<Controller.State> first = f.ctl.submit(START);
                check(f.ctl.submit(START).isCompletedExceptionally(), "drop second tap");
                first.get(2, TimeUnit.SECONDS);
                check(f.camera.starts == 1, "only one start");
            }
        });
        test("Verlorene Startantwort führt zu Statusabgleich ohne Wiederholung", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.loseStartAck = true;
                check(f.act(START).recording() == Recording.RECORDING, "reconcile after timeout");
                check(f.camera.starts == 1, "no replay");
            }
        });
        test("ACK ohne Kameraänderung bestätigt keine Aufnahme", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.ignoreCommands = true;
                check(f.act(START).recording() == Recording.UNKNOWN, "unknown outcome");
                check(f.camera.starts == 1, "no retry");
            }
        });
        test("Funkabbruch bewahrt letzte bestätigte Aufnahme", () -> {
            try (Fixture f = new Fixture()) {
                f.act(START); f.camera.drop();
                check(f.ctl.state().recording() == Recording.UNKNOWN, "current unknown");
                check(f.ctl.state().lastConfirmed() == Recording.RECORDING, "last known retained");
                f.act(CONNECT);
                check(f.ctl.state().recording() == Recording.RECORDING, "real state reconciled");
                check(f.camera.starts == 1, "no unintended start");
            }
        });
        test("Physischer Auslöser bestimmt Anzeige", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.camera.physicalShutter(true);
                check(f.ctl.state().recording() == Recording.RECORDING, "physical start");
                f.camera.physicalShutter(false);
                check(f.ctl.state().recording() == Recording.IDLE, "physical stop");
            }
        });
        test("Foto- und unbekannter Modus werden nicht verändert", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.mode = Mode.OTHER; f.act(START);
                check(f.camera.starts == 0 && f.camera.mode == Mode.OTHER, "photo unchanged");
                f.camera.mode = Mode.UNKNOWN; f.act(START);
                check(f.camera.starts == 0, "unknown mode blocks start");
            }
        });
        test("Unbekannte Statuswerte lösen keinen Befehl aus", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.recording = Recording.TRANSITION; f.act(START);
                check(f.camera.starts == 0, "saving/transition is not idle");
            }
        });
        test("Veraltete Daten werden unbekannt", () -> {
            try (Fixture f = new Fixture()) {
                f.act(START); f.now.addAndGet(1001);
                check(f.ctl.state().recording() == Recording.UNKNOWN, "TTL");
                f.camera.staleQueries = true; f.act(REFRESH);
                check(f.ctl.state().recording() == Recording.UNKNOWN, "stale poll rejected");
            }
        });
        test("Stopp bei Funkabbruch löscht Pausenabsicht", () -> {
            try (Fixture f = new Fixture()) {
                f.act(START); f.act(PAUSE); f.camera.drop(); f.camera.powered = false;
                check(!f.act(STOP).pauseIntent(), "stop intent survives failure");
                f.camera.powered = true; f.act(CONNECT);
                check(f.camera.starts == 1, "reconnect never resumes");
            }
        });
        test("Neustart mit gespeicherter Pause startet keinen Clip", () -> {
            AtomicLong time = new AtomicLong(1);
            SimulatedCamera camera = new SimulatedCamera(time::get);
            try (Controller ctl = new Controller(camera, time::get, FAST, true, p -> {})) {
                ctl.restore(CAMERA); ctl.submit(CONNECT).get(2, TimeUnit.SECONDS);
                check(ctl.state().pauseIntent() && camera.starts == 0, "restore pause, no auto start");
            }
        });
        test("Ace Pro 2 wird nicht als ursprüngliche Ace Pro akzeptiert", () -> {
            boolean rejected = false;
            try { new Camera("AA", "Ace Pro 2", "ACE_PRO_2"); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "strict model");
        });
        test("Kamerawechsel während Verbindung gesperrt", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); boolean rejected = false;
                try { f.ctl.select(CAMERA); } catch (IllegalStateException expected) { rejected = true; }
                check(rejected, "one selected camera");
            }
        });
        test("Beendete Sitzung nimmt keine Befehle an", () -> {
            Fixture f = new Fixture(); f.close();
            check(f.ctl.submit(START).isCompletedExceptionally(), "closed");
        });
        test("Ausschalten stoppt und bestätigt den Clip vor dem Aus-Befehl", () -> {
            try (Fixture f = new Fixture()) {
                f.act(START); Controller.State state = f.act(POWER_OFF);
                check(f.camera.stops == 1 && f.camera.powerOffs == 1, "stop before power off");
                check(state.lastConfirmed() == Recording.IDLE && state.recording() == Recording.UNKNOWN, "idle confirmed, power unknown");
                check(state.power() == Controller.Power.OFF_REQUESTED && !state.pauseIntent(), "request, not confirmed off");
            }
        });
        test("Unbestätigter Aufnahmestopp verhindert Ausschalten", () -> {
            try (Fixture f = new Fixture()) {
                f.act(START); f.camera.ignoreCommands = true; f.act(POWER_OFF);
                check(f.camera.powerOffs == 0 && f.camera.stops == 1, "do not interrupt save/capture blindly");
            }
        });
        test("Verlorene Ausschaltantwort führt zu keiner Wiederholung", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.camera.losePowerOffAck = true;
                f.act(POWER_OFF);
                check(f.ctl.submit(POWER_OFF).isCompletedExceptionally(), "off request is never replayed");
                check(f.camera.powerOffs == 1 && f.camera.wakes == 0, "no resend, no implicit wake");
                check(f.ctl.state().power() == Controller.Power.OFF_REQUESTED, "uncertain power retained");
            }
        });
        test("Aus-Befehl gibt auch eine weiter eingeschaltete Kamera frei", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.camera.ignorePowerOff = true; f.act(POWER_OFF);
                check(f.ctl.state().power() == Controller.Power.OFF_REQUESTED, "one-way ACK not off proof");
                check(!f.camera.connected && f.camera.disconnects > 0, "release even if shutdown ignored");
                check(f.ctl.submit(REFRESH).isCompletedExceptionally(), "status must not reopen off camera");
                f.act(CONNECT); check(f.ctl.state().power() == Controller.Power.ON, "explicit camera reply confirms on");
            }
        });
        test("Aus ruht über viele Poll-Takte ohne Status oder Wiederverbindung", () -> {
            AtomicLong clock = new AtomicLong(100);
            SimulatedCamera camera = new SimulatedCamera(clock::get);
            Controller.Timing timing = new Controller.Timing(200, 100, 30, 100, 20, 1000);
            try (Controller ctl = new Controller(camera, clock::get, timing, false, p -> {})) {
                ctl.restore(CAMERA); ctl.submit(CONNECT).get(2, TimeUnit.SECONDS);
                camera.ignorePowerOff = true;
                ctl.submit(POWER_OFF).get(2, TimeUnit.SECONDS);
                int queries = camera.queries;
                camera.listener.status(new Status(Recording.IDLE, Mode.VIDEO, 50, 0L, clock.get()));
                camera.drop(); Thread.sleep(220);
                check(camera.queries == queries && camera.connects == 1 && camera.wakes == 0, "silent after shutdown");
                check(ctl.state().power() == Controller.Power.OFF_REQUESTED && ctl.state().recording() == Recording.UNKNOWN,
                    "late status cannot restore active session");
            }
        });
        test("Status Stopp Pause nach Aus öffnen keine Bluetooth-Sitzung", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.act(POWER_OFF);
                int reads = f.camera.queries;
                for (Controller.Action action : new Controller.Action[]{REFRESH, STOP, PAUSE, POWER_OFF})
                    check(f.ctl.submit(action).isCompletedExceptionally(), "manual inactive command blocked");
                check(f.camera.queries == reads && f.camera.connects == 1 && f.camera.wakes == 0, "no hidden connect/wake");
                check(f.act(START).recording() == Recording.RECORDING && f.camera.starts == 1 && f.camera.wakes == 1,
                    "explicit start may wake and starts only one clip");
            }
        });
        test("Sitzungsende beendet auch bei ignoriertem Aus jede eigene Abfrage", () -> {
            Fixture f = new Fixture();
            f.act(CONNECT); f.camera.ignorePowerOff = true; f.act(POWER_OFF); f.close();
            int reads = f.camera.queries;
            Thread.sleep(50);
            check(!f.camera.connected && f.camera.listener == null && f.camera.queries == reads, "release on close");
        });
        test("Aufwecken nach Aus gleicht Status ab und startet nicht", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.act(POWER_OFF); f.act(WAKE);
                check(f.ctl.state().power() == Controller.Power.ON && f.camera.starts == 0, "wake only");
            }
        });
        test("Ausschalten löst auch nach Reconnect-Frist keine Verbindung aus", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.act(POWER_OFF); Thread.sleep(5200);
                check(f.camera.connects == 1 && f.camera.wakes == 0, "intentional off suppresses reconnect");
            }
        });
        test("Aufwecken bleibt bei laufender Hintergrundabfrage bedienbar", () -> {
            AtomicLong clock = new AtomicLong(1);
            SimulatedCamera camera = new SimulatedCamera(clock::get);
            Controller.Timing timing = new Controller.Timing(200, 2000, 30, 100, 20, 1000);
            try (Controller ctl = new Controller(camera, clock::get, timing, false, p -> {})) {
                ctl.restore(CAMERA); ctl.submit(CONNECT).get(2, TimeUnit.SECONDS);
                CompletableFuture<Status> pending = new CompletableFuture<>(); camera.nextQuery = pending;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (!(ctl.state().busy() && ctl.state().background()) && System.nanoTime() < deadline) Thread.sleep(2);
                check(RideControls.enabled(RideControls.Slot.WAKE, ctl.state(), true, true), "wake usable during background status");
                camera.powered = false; // BLE cache still says connected, as after camera sleep.
                ctl.submit(WAKE).get(1, TimeUnit.SECONDS);
                check(pending.isCancelled() && camera.wakes == 1 && camera.starts == 0, "cancel read, wake exactly once");
                check(ctl.state().connection() == Controller.Connection.CONNECTED && ctl.state().recording() == Recording.IDLE, "verify real readiness");
            }
        });
        test("Veraltete BLE-Verbindung verhindert explizites Aufwecken nicht", () -> {
            try (Fixture f = new Fixture()) {
                f.act(CONNECT); f.camera.powered = false; f.act(WAKE);
                check(f.camera.wakes == 1 && f.camera.connects == 2 && f.camera.starts == 0, "wake after stale connection");
            }
        });
        test("Aufwecken unterbricht passiven Wiederverbindungsversuch", () -> {
            AtomicLong clock = new AtomicLong(1);
            SimulatedCamera camera = new SimulatedCamera(clock::get);
            Controller.Timing timing = new Controller.Timing(30000, 1000, 30, 100, 10000, 1000);
            try (Controller ctl = new Controller(camera, clock::get, timing, false, p -> {})) {
                ctl.restore(CAMERA); ctl.submit(CONNECT).get(2, TimeUnit.SECONDS);
                CompletableFuture<Void> pending = new CompletableFuture<>(); camera.nextConnect = pending;
                camera.powered = false; camera.drop();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
                while (!(ctl.state().busy() && ctl.state().background()) && System.nanoTime() < deadline) Thread.sleep(5);
                check(ctl.state().background() && RideControls.enabled(RideControls.Slot.WAKE, ctl.state(), true, true), "wake enabled during reconnect");
                ctl.submit(WAKE).get(1, TimeUnit.SECONDS);
                check(pending.isCancelled() && camera.wakes == 1 && camera.connects == 3 && camera.starts == 0, "cancel reconnect, wake once and verify");
            }
        });
        test("Akku und Speicher behalten ihre eigene Frische trotz Capture-Polling", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.reportedBatteryAt = 100L;
                f.camera.reportedStorage = new Storage(StorageState.AVAILABLE, 100L, 200L, 100L);
                f.act(CONNECT);
                check(f.ctl.state().battery() == 76 && f.ctl.state().storage().freeBytes() == 100, "fresh quantities");
                f.now.addAndGet(31000); f.act(REFRESH);
                check(f.ctl.state().recording() == Recording.IDLE, "capture remains fresh");
                check(f.ctl.state().battery() == null && f.ctl.state().storage().state() == StorageState.UNKNOWN, "no refreshed age from capture query");
            }
        });
        test("Mengenwerte mit Zeitstempel aus der Zukunft werden verworfen", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.reportedBatteryAt = 5000L;
                f.camera.reportedStorage = new Storage(StorageState.AVAILABLE, 100L, 200L, 5000L);
                f.act(CONNECT);
                check(f.ctl.state().battery() == null && f.ctl.state().storage().state() == StorageState.UNKNOWN, "future samples unknown");
            }
        });
        test("Funkabbruch löscht Mengenwerte und bestätigte Clipdauer", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.reportedStorage = new Storage(StorageState.AVAILABLE, 100L, 200L, 100L);
                f.act(START); f.camera.drop();
                check(f.ctl.state().seconds() == null && f.ctl.state().battery() == null && f.ctl.state().storage().state() == StorageState.UNKNOWN, "unknown after drop");
                check(f.ctl.state().lastConfirmed() == Recording.RECORDING, "capture history retained");
            }
        });
        test("Fehlender Kamera-Zähler erlaubt geschätzte Dauer nur nach bestätigtem Start", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.reportedSeconds = null; f.act(START);
                check(f.ctl.state().seconds() == 0 && f.ctl.state().durationEstimated(), "start observed");
                f.now.addAndGet(3000); f.act(REFRESH);
                check(f.ctl.state().seconds() >= 3 && f.ctl.state().durationEstimated(), "clock progresses on confirmed capture");
                f.act(PAUSE); check(f.ctl.state().seconds() == 0, "pause stops timer");
                f.act(RESUME); check(f.ctl.state().seconds() == 0 && f.ctl.state().durationEstimated(), "new clip resets");
            }
        });
        test("Wiederverbindung ohne Kamera-Zähler erfindet keine Clipdauer", () -> {
            try (Fixture f = new Fixture()) {
                f.camera.reportedSeconds = null; f.act(START); f.camera.drop(); f.act(CONNECT);
                check(f.ctl.state().recording() == Recording.RECORDING && f.ctl.state().seconds() == null, "recording confirmed, total duration unknown");
            }
        });
        test("Manuelle Statusprüfung unterbricht Hintergrundlesen ohne Clipdauer zu verlieren", () -> {
            AtomicLong time = new AtomicLong(100);
            SimulatedCamera camera = new SimulatedCamera(time::get); camera.reportedSeconds = null;
            Controller.Timing timing = new Controller.Timing(200, 2000, 30, 100, 20, 10000);
            try (Controller ctl = new Controller(camera, time::get, timing, false, p -> {})) {
                ctl.restore(CAMERA); ctl.submit(START).get(2, TimeUnit.SECONDS);
                time.addAndGet(3000);
                CompletableFuture<Status> pending = new CompletableFuture<>(); camera.nextQuery = pending;
                long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
                while (!(ctl.state().busy() && ctl.state().background() && camera.nextQuery == null) && System.nanoTime()<deadline) Thread.sleep(2);
                check(camera.nextQuery == null && ctl.state().background(), "background read owns the pending query before preemption");
                ctl.submit(REFRESH).get(1,TimeUnit.SECONDS);
                check(pending.isCancelled() && ctl.state().seconds()!=null && ctl.state().seconds()>=3,
                        "preempted read does not reset a confirmed ongoing clip");
                check(ctl.state().durationEstimated() && camera.starts==1, "no new capture from status refresh");
            }
        });
        passed += TelemetryTest.run();
        passed += QuietReleaseTest.run();
        passed += FieldControlsTest.run();
        passed += RideControlsTest.run();
        passed += AdapterTest.run();
        passed += SdkStatusTest.run();
        passed += MultiCameraTest.run();
        passed += LanguageTest.run();
        System.out.println("SIMULATION_TESTS=" + passed + " HARDWARE_TESTS=0");
    }
}
