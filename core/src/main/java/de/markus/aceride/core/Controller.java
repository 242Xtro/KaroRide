package de.markus.aceride.core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.CopyOnWriteArrayList;
import static de.markus.aceride.core.CameraAdapter.*;

/** One selected camera, one serialized command. No ride/sensor-triggered recording. */
public final class Controller implements AutoCloseable {
    public enum Action { CONNECT, WAKE, START, STOP, PAUSE, RESUME, REFRESH, POWER_OFF }
    public enum Connection { DISCONNECTED, CONNECTING, CONNECTED }
    public enum Power { UNKNOWN, ON, OFF_REQUESTED }
    public record State(Connection connection, Recording recording, Recording lastConfirmed,
                        long lastConfirmedAt, Integer battery, Long seconds, boolean pauseIntent,
                        boolean busy, String message, Mode mode, Power power, boolean background,
                        Storage storage, boolean durationEstimated, String telemetryInfo, Camera camera) {
        public State(Connection connection, Recording recording, Recording lastConfirmed, long lastConfirmedAt,
                     Integer battery, Long seconds, boolean pauseIntent, boolean busy, String message,
                     Mode mode, Power power, boolean background, Storage storage, boolean durationEstimated, String telemetryInfo) {
            this(connection, recording, lastConfirmed, lastConfirmedAt, battery, seconds, pauseIntent, busy,
                    message, mode, power, background, storage, durationEstimated, telemetryInfo, null);
        }
        public State(Connection connection, Recording recording, Recording lastConfirmed, long lastConfirmedAt,
                     Integer battery, Long seconds, boolean pauseIntent, boolean busy, String message,
                     Mode mode, Power power, boolean background) {
            this(connection, recording, lastConfirmed, lastConfirmedAt, battery, seconds, pauseIntent, busy,
                    message, mode, power, background, Storage.unknown(), false, "");
        }
    }
    public record Timing(long connect, long query, long ack, long confirm, long poll, long stale) {
        public static Timing production() { return new Timing(60000, 6000, 4000, 12000, 3000, 12000); }
    }
    private final CameraAdapter adapter;
    private final LongSupplier clock;
    private final Timing timing;
    private final Consumer<Boolean> savePause;
    private final Diagnostics diagnostics;
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final CopyOnWriteArrayList<Consumer<State>> observers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private boolean background;
    private long commandGeneration;
    private Future<?> commandTask;
    private CompletableFuture<State> commandResult;
    private volatile boolean closed;
    private Camera selected;
    private Connection connection = Connection.DISCONNECTED;
    private Power power = Power.UNKNOWN;
    private boolean suppressReconnect;
    private Recording recording = Recording.UNKNOWN, lastConfirmed = Recording.UNKNOWN;
    private Mode mode = Mode.UNKNOWN;
    private Integer battery;
    private Long seconds;
    private long batteryAt = -1;
    private Storage storage = Storage.unknown();
    private String telemetryInfo = "";
    private final CaptureClock captureClock = new CaptureClock();
    private boolean pauseIntent;
    private long lastAt = -1, lastRead = -1, epoch;
    private int reconnectAttempts;
    private boolean reconnectScheduled;
    private String message = "Kamera auswählen";

    public Controller(CameraAdapter adapter, LongSupplier clock, Timing timing,
                      boolean savedPause, Consumer<Boolean> savePause) {
        this(adapter, clock, timing, savedPause, savePause, Diagnostics.NONE);
    }
    public Controller(CameraAdapter adapter, LongSupplier clock, Timing timing,
                      boolean savedPause, Consumer<Boolean> savePause, Diagnostics diagnostics) {
        this.adapter = adapter; this.clock = clock; this.timing = timing;
        this.diagnostics = diagnostics;
        diagnostics.record(Diagnostics.Event.SESSION_OPEN);
        this.pauseIntent = savedPause; this.savePause = savePause;
        adapter.setListener(new Listener() {
            @Override public void status(Status value) {
                synchronized (Controller.this) {
                    if (closed || connection != Connection.CONNECTED || power == Power.OFF_REQUESTED) return;
                    apply(value);
                }
            }
            @Override public void disconnected(String reason) { lost(reason); }
        });
        scheduler.scheduleWithFixedDelay(this::poll, timing.poll, timing.poll, TimeUnit.MILLISECONDS);
    }

    public synchronized void select(Camera camera) {
        if (closed || busy.get()) throw new IllegalStateException("Bitte laufenden Vorgang abwarten.");
        if (connection == Connection.CONNECTED)
            throw new IllegalStateException("Vor einem Kamerawechsel zuerst trennen.");
        selected = camera; epoch++; reconnectAttempts = 0;
        power = Power.UNKNOWN; suppressReconnect = false;
        recording = lastConfirmed = Recording.UNKNOWN; lastAt = lastRead = -1;
        battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate(); mode = Mode.UNKNOWN;
        setPause(false); message = "Kamera gespeichert – noch nicht verbunden"; publish();
    }
    /** Restore does not clear a saved pause. A reboot must never resume capture. */
    public synchronized void restore(Camera camera) {
        if (selected != null) throw new IllegalStateException("Kamera bereits ausgewählt");
        selected = camera; message = "Gespeicherte Kamera – Verbindung unbekannt"; publish();
    }
    public synchronized Camera selectedCamera() { return selected; }
    public synchronized boolean reconnectPending() { return reconnectScheduled; }
    /** One explicit switch, no capture command. Persist first so a write failure preserves the live selection. */
    public void switchCamera(Camera camera, boolean savedPause, Runnable persist) {
        synchronized (this) {
            if (closed || (busy.get() && !background)) throw new IllegalStateException("Bitte laufenden Vorgang abwarten.");
            if (state().recording() == Recording.RECORDING || state().recording() == Recording.TRANSITION)
                throw new IllegalStateException("Vor dem Kamerawechsel die Aufnahme stoppen oder pausieren.");
            persist.run();
            epoch++; commandGeneration++;
            if (commandTask != null) commandTask.cancel(true);
            if (commandResult != null && !commandResult.isDone())
                commandResult.completeExceptionally(new CancellationException("Kamera gewechselt"));
            busy.set(true); background = false; suppressReconnect = true; reconnectAttempts = 0; reconnectScheduled = false;
            connection = Connection.DISCONNECTED; power = Power.UNKNOWN;
        }
        // SDK callbacks take the adapter lock before the Controller lock. Never
        // acquire the adapter while holding ours, even during a UI camera switch.
        try { adapter.disconnect(); }
        finally {
            synchronized (this) {
                selected = camera; pauseIntent = savedPause;
                recording = lastConfirmed = Recording.UNKNOWN; mode = Mode.UNKNOWN;
                lastAt = lastRead = -1; batteryAt = -1; battery = null; seconds = null;
                storage = Storage.unknown(); telemetryInfo = ""; captureClock.invalidate();
                busy.set(false); message = "Kamera gewechselt – Verbindung und Status prüfen"; publish();
            }
        }
    }
    public synchronized State state() {
        Recording shown = recording;
        if (connection != Connection.CONNECTED || lastAt < 0 || clock.getAsLong()-lastAt > timing.stale)
            shown = Recording.UNKNOWN;
        boolean fresh = connection == Connection.CONNECTED && lastRead >= 0 && clock.getAsLong()-lastRead <= timing.stale;
        long now = clock.getAsLong();
        boolean batteryFresh = fresh && batteryAt >= 0 && batteryAt <= now && now-batteryAt <= 30000;
        boolean storageFresh = fresh && storage.readAtMs() >= 0 && storage.readAtMs() <= now && now-storage.readAtMs() <= 30000;
        CaptureClock.Duration duration = captureClock.display(shown, now);
        return new State(connection, shown, lastConfirmed, lastAt, batteryFresh ? battery : null, duration.seconds(),
                pauseIntent, busy.get(), message, mode,
                power == Power.ON && !fresh ? Power.UNKNOWN : power, background,
                storageFresh ? storage : Storage.unknown(), duration.estimated(), telemetryInfo, selected);
    }
    public void observe(Consumer<State> observer) { observers.add(observer); observer.accept(state()); }
    public void removeObserver(Consumer<State> observer) { observers.remove(observer); }
    private synchronized void publish() {
        State snapshot = state();
        diagnostics.state(snapshot);
        for (Consumer<State> observer : observers) {
            try { observer.accept(snapshot); } catch (RuntimeException ignored) { /* isolated UI */ }
        }
    }
    private synchronized void setPause(boolean value) { pauseIntent = value; savePause.accept(value); }

    /** Duplicate taps are dropped, not queued for later accidental playback. */
    public CompletableFuture<State> submit(Action action) {
        return submit(action, false);
    }
    /** Fence actions rendered for another camera, including still-visible old RemoteViews. */
    public synchronized CompletableFuture<State> submitForCamera(Action action, String address) {
        if (selected == null || address == null || !selected.address().equals(address))
            return CompletableFuture.failedFuture(new IllegalStateException("Kamera gewechselt. Aktuelles Feld erneut bedienen."));
        return submit(action, false);
    }
    private synchronized CompletableFuture<State> submit(Action action, boolean quiet) {
        diagnostics.record(Diagnostics.Event.ACTION_REQUEST, action);
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Sitzung beendet"));
        if (quiet && suppressReconnect) {
            diagnostics.record(Diagnostics.Event.ACTION_DROPPED, action);
            return CompletableFuture.failedFuture(new IllegalStateException("Kameraverbindung ruht"));
        }
        if (power == Power.OFF_REQUESTED && action != Action.CONNECT && action != Action.WAKE &&
                action != Action.START && action != Action.RESUME) {
            diagnostics.record(Diagnostics.Event.ACTION_DROPPED, action);
            return CompletableFuture.failedFuture(new IllegalStateException("Kamera ausgeschaltet angefordert. Zum Bedienen Aufwecken oder Verbinden wählen."));
        }
        if (busy.get()) {
            if (quiet || !background) return CompletableFuture.failedFuture(
                    new IllegalStateException("Ein Kamerabefehl läuft bereits."));
            // Only read-only status/reconnect work may be preempted. Never cancel
            // START/STOP/PAUSE/RESUME/POWER_OFF to replay a capture command.
            epoch++;
            if (commandTask != null) commandTask.cancel(true);
            if (commandResult != null) commandResult.completeExceptionally(new CancellationException("Hintergrundabfrage durch Bedienung ersetzt"));
        }
        long lease = ++commandGeneration;
        background = quiet; busy.set(true);
        synchronized (this) {
            if (selected == null) {
                busy.set(false);
                return CompletableFuture.failedFuture(new IllegalStateException("Zuerst Kamera auswählen."));
            }
            // STOP clears intent even if communication fails. Reconnection only reconciles.
            if (action == Action.STOP) setPause(false);
            if (action == Action.PAUSE) setPause(true);
            if (action == Action.CONNECT || action == Action.WAKE || action == Action.START || action == Action.RESUME) {
                suppressReconnect = false;
                if (connection != Connection.CONNECTED || power == Power.OFF_REQUESTED) power = Power.UNKNOWN;
            }
            if (!quiet) message = switch (action) {
                case CONNECT -> "Verbinden …"; case WAKE -> "Aufwecken und prüfen …";
                case START -> "Start angefordert …"; case STOP -> "Stopp angefordert …";
                case PAUSE -> "Pause angefordert …"; case RESUME -> "Fortsetzen angefordert …";
                case REFRESH -> "Kamerazustand abfragen …";
                case POWER_OFF -> "Aufnahme prüfen, dann Kamera ausschalten …";
            };
            publish();
        }
        CompletableFuture<State> result = new CompletableFuture<>();
        commandResult = result;
        commandTask = commands.submit(() -> {
            try {
                execute(action);
                synchronized (this) {
                    if (!quiet && lease == commandGeneration) message = action == Action.POWER_OFF
                        ? "Aus angefordert – Ausschalten an der Kamera prüfen."
                        : "Kamerazustand bestätigt";
                }
            } catch (Exception ex) {
                diagnostics.record(Diagnostics.Event.ACTION_ERROR, action);
                synchronized (this) {
                    if (lease == commandGeneration) {
                        if (!quiet) message = readable(ex);
                        else if (recording == Recording.UNKNOWN) message = "Status unbekannt. Verbindung oder Aufwecken prüfen.";
                    }
                }
            } finally {
                diagnostics.record(Diagnostics.Event.ACTION_END, action);
                synchronized (this) {
                    if (lease == commandGeneration) { busy.set(false); background = false; publish(); }
                }
                result.complete(state());
            }
        });
        return result;
    }

    private void execute(Action action) throws Exception {
        if (closed) throw new IllegalStateException("Sitzung beendet");
        if (action == Action.POWER_OFF && !CameraModels.shutdown(selectedCamera().model()))
            throw new IllegalStateException("GO 3S bitte an der Kamera ausschalten. Dieser SDK-Befehl wird nicht unterstützt.");
        if (action == Action.RESUME && !state().pauseIntent)
            throw new IllegalStateException("Keine lokale Pause. Für einen neuen Clip Start verwenden.");
        if (action == Action.POWER_OFF && state().connection != Connection.CONNECTED)
            throw new IllegalStateException("Zum Ausschalten zuerst die eingeschaltete Kamera verbinden.");
        if (action == Action.WAKE && state().connection == Connection.CONNECTED) {
            // A cached BLE connection can outlive the camera's automatic sleep.
            // A real reply means it is awake; a failed query requires a new wake path.
            Status current = null;
            try { current = query(); }
            catch (Exception staleLink) {
                adapter.disconnect();
                synchronized (this) { epoch++; connection = Connection.DISCONNECTED; power = Power.UNKNOWN; }
            }
            if (current != null) {
                if (current.recording() == Recording.IDLE || current.recording() == Recording.RECORDING) return;
                throw new IllegalStateException("Kamera antwortet; Aufnahmezustand noch unbekannt.");
            }
        }
        ensureConnection(action == Action.WAKE || action == Action.START || action == Action.RESUME,
                action == Action.WAKE);
        Status before = query();
        if (action == Action.CONNECT || action == Action.WAKE || action == Action.REFRESH) {
            if (before.recording() == Recording.UNKNOWN || before.recording() == Recording.TRANSITION)
                throw new IllegalStateException("Verbunden; Aufnahmezustand noch unbekannt.");
            return;
        }
        if (before.recording() == Recording.UNKNOWN || before.recording() == Recording.TRANSITION)
            throw new IllegalStateException("Aufnahmezustand unklar. Erst Status erneut prüfen.");
        boolean starting = action == Action.START || action == Action.RESUME;
        if (starting && before.recording() == Recording.RECORDING) { setPause(false); return; }
        if (!starting && before.recording() == Recording.IDLE) {
            if (action == Action.POWER_OFF) requestPowerOff();
            return;
        }
        if (starting && before.mode() != Mode.VIDEO)
            throw new IllegalStateException("Videomodus nicht bestätigt. An der Kamera Video wählen.");
        confirmCapture(starting);
        if (action == Action.POWER_OFF) requestPowerOff();
    }

    private void confirmCapture(boolean starting) throws Exception {
        long sentAt = clock.getAsLong();
        if (closed) throw new IllegalStateException("Sitzung beendet");
        CompletableFuture<Void> ack = starting ? adapter.startVideo() : adapter.stopVideo();
        // Even an error/timeout may follow a command executed on the camera. Never resend.
        try { waitFor(ack, timing.ack); } catch (Exception ignored) { /* reconcile once */ }
        Recording target = starting ? Recording.RECORDING : Recording.IDLE;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timing.confirm);
        while (!closed && System.nanoTime() < deadline) {
            State observed = state();
            synchronized (this) {
                if (observed.connection == Connection.CONNECTED && recording == target && lastRead >= sentAt) {
                    if (starting) setPause(false);
                    return;
                }
            }
            try {
                Status now = query();
                if (now.recording() == target && now.readStartedAtMs() >= sentAt) {
                    if (starting) setPause(false);
                    return;
                }
            } catch (Exception ignored) { /* bounded status queries, no capture command replay */ }
            Thread.sleep(Math.min(250, timing.poll));
        }
        synchronized (this) { recording = Recording.UNKNOWN; battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate(); publish(); }
        throw new IllegalStateException("Befehl gesendet, Ergebnis unklar. Kamera kann weiter aufnehmen. Status prüfen.");
    }

    private void requestPowerOff() throws Exception {
        synchronized (this) {
            if (closed || connection != Connection.CONNECTED || recording != Recording.IDLE)
                throw new IllegalStateException("Aufnahmestopp nicht bestätigt. Kamera bleibt eingeschaltet.");
            // Intentional shutdown must not trigger a reconnect that wakes the camera.
            suppressReconnect = true; reconnectAttempts = 3; epoch++;
            reconnectScheduled = false; power = Power.OFF_REQUESTED;
            setPause(false); publish();
        }
        // The SDK exposes no ACK or power-off status. Never retry this request.
        try { waitFor(adapter.powerOff(), timing.ack); }
        catch (Exception ex) {
            throw new IllegalStateException("Ausschalten angefordert, Ergebnis unklar. Kamera prüfen.", ex);
        } finally {
            // Release even when the one-way request times out or the camera keeps
            // its GATT link alive. A disconnect is not proof of camera power-off.
            adapter.disconnect();
            synchronized (this) {
                epoch++; connection = Connection.DISCONNECTED; recording = Recording.UNKNOWN;
                mode = Mode.UNKNOWN; battery = null; seconds = null; batteryAt = -1;
                storage = Storage.unknown(); captureClock.invalidate(); publish();
            }
        }
    }

    private void ensureConnection(boolean allowWake, boolean forceWake) throws Exception {
        if (state().connection == Connection.CONNECTED) return;
        Camera camera;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Sitzung beendet");
            camera = selected; connection = Connection.CONNECTING; publish();
        }
        try {
            if (forceWake && !adapter.wakesOnConnect(camera)) {
                if (!adapter.supportsWake(camera))
                    throw new IllegalStateException("Aufwecken für diese Kamera/Hardware nicht verfügbar. Kamera manuell einschalten.");
                waitFor(adapter.wakeSignal(camera), timing.connect);
            }
            try { waitFor(adapter.connect(camera), timing.connect); }
            catch (Exception failedConnect) {
                adapter.disconnect();
                if (forceWake || !allowWake || adapter.wakesOnConnect(camera) || !adapter.supportsWake(camera)) throw failedConnect;
                synchronized (this) { connection = Connection.CONNECTING; }
                waitFor(adapter.wakeSignal(camera), timing.connect);
                waitFor(adapter.connect(camera), timing.connect);
            }
            synchronized (this) {
                if (closed) throw new IllegalStateException("Sitzung beendet");
                connection = Connection.CONNECTED; recording = Recording.UNKNOWN;
                power = Power.UNKNOWN;
                lastRead = -1; mode = Mode.UNKNOWN; reconnectAttempts = 0; publish();
            }
        } catch (Exception failure) {
            adapter.disconnect();
            synchronized (this) {
                connection = Connection.DISCONNECTED; recording = Recording.UNKNOWN;
                power = Power.UNKNOWN;
                mode = Mode.UNKNOWN; battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate(); publish();
            }
            throw failure;
        }
    }
    private Status query() throws Exception {
        diagnostics.record(Diagnostics.Event.STATUS_REQUEST);
        long queryEpoch;
        synchronized (this) { queryEpoch = epoch; }
        long startedAt = clock.getAsLong();
        try {
            Status status = waitFor(adapter.freshStatus(), timing.query);
            synchronized (this) {
                if (queryEpoch != epoch || connection != Connection.CONNECTED)
                    throw new IllegalStateException("Verbindung während Statusabfrage verloren.");
                if (status.readStartedAtMs() < startedAt || status.readStartedAtMs() > clock.getAsLong())
                    throw new IllegalStateException("Statusabfrage lieferte veraltete oder ungültige Zeitdaten.");
                apply(status);
                // A later, verified event/query may arrive while this query is in flight.
                // Do not act on its older snapshot after a physical camera button press.
                if (lastRead > status.readStartedAtMs())
                    status = new Status(recording, mode, battery, seconds, lastRead, storage, batteryAt, telemetryInfo);
            }
            return status;
        } catch (Exception failure) {
            diagnostics.record(Diagnostics.Event.STATUS_ERROR);
            synchronized (this) {
                // Intentional preemption of a read does not invalidate a still-fresh
                // confirmed capture. Actual loss/errors still invalidate clip continuity.
                if (queryEpoch == epoch || connection != Connection.CONNECTED || closed) {
                    recording = Recording.UNKNOWN; mode = Mode.UNKNOWN;
                    battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate();
                }
                publish();
            }
            throw failure;
        }
    }
    private synchronized void apply(Status status) {
        if (status.readStartedAtMs() < lastRead) return;
        lastRead = status.readStartedAtMs();
        if (clock.getAsLong() - lastRead > timing.stale) {
            recording = Recording.UNKNOWN; mode = Mode.UNKNOWN; battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate();
            publish(); return;
        }
        captureClock.observe(recording, status);
        recording = status.recording(); mode = status.mode();
        diagnostics.status(status);
        power = Power.ON; // Only a fresh camera reply confirms power-on.
        battery = status.battery(); seconds = status.seconds();
        batteryAt = status.batteryReadAtMs(); storage = status.storage(); telemetryInfo = status.telemetryInfo();
        if (recording == Recording.IDLE || recording == Recording.RECORDING) {
            lastConfirmed = recording; lastAt = lastRead;
            // The physical camera shutter wins over a previous local pause.
            if (recording == Recording.RECORDING && (!busy.get() || background)) setPause(false);
        }
        publish();
    }
    private synchronized void poll() {
        if (closed) return;
        publish(); // makes staleness visible even when queries fail
        if (!suppressReconnect && power != Power.OFF_REQUESTED &&
                connection == Connection.CONNECTED && !busy.get()) submit(Action.REFRESH, true);
    }
    private void lost(String reason) {
        boolean releaseGo;
        synchronized (this) {
            if (closed) return;
            diagnostics.record(Diagnostics.Event.CONNECTION_LOST);
            epoch++; connection = Connection.DISCONNECTED; recording = Recording.UNKNOWN;
            reconnectScheduled = false;
            mode = Mode.UNKNOWN; battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate();
            if (power != Power.OFF_REQUESTED) power = Power.UNKNOWN;
            message = power == Power.OFF_REQUESTED ? "Aus angefordert – Ausschalten an der Kamera prüfen."
                    : "Verbindung verloren – Kamera kann weiter aufnehmen"; publish();
            releaseGo = selected != null && adapter.wakesOnConnect(selected);
            scheduleReconnect();
        }
        // End the SDK's GO heartbeat as well; no passive connection may wake it.
        if (releaseGo) adapter.disconnect();
    }
    private synchronized void scheduleReconnect() {
        if (closed || suppressReconnect || selected == null || reconnectScheduled || reconnectAttempts >= 3) return;
        // GO authorization during connect may wake the camera. Only an explicit tap may reopen it.
        if (adapter.wakesOnConnect(selected)) return;
        reconnectScheduled = true;
        diagnostics.record(Diagnostics.Event.RECONNECT_SCHEDULED);
        int attempt = ++reconnectAttempts;
        long scheduledEpoch = epoch;
        scheduler.schedule(() -> {
            synchronized (this) {
                if (closed || scheduledEpoch != epoch) return;
                reconnectScheduled = false;
            }
            if (closed || state().connection != Connection.DISCONNECTED) return;
            if (busy.get()) { synchronized (this) { reconnectAttempts--; scheduleReconnect(); } return; }
            submit(Action.CONNECT, true).whenComplete((s,e) -> {
                synchronized (this) {
                    if (!closed && scheduledEpoch == epoch && state().connection == Connection.DISCONNECTED) scheduleReconnect();
                }
            });
        }, 5L * attempt, TimeUnit.SECONDS);
    }
    public void disconnect() {
        synchronized (this) {
            if (busy.get()) throw new IllegalStateException("Bitte Vorgang abwarten");
            epoch++; reconnectAttempts = 3;
            suppressReconnect = true; power = Power.UNKNOWN;
            connection = Connection.DISCONNECTED; recording = Recording.UNKNOWN;
            mode = Mode.UNKNOWN; battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate();
            message = "Getrennt – Aufnahme an der Kamera prüfen"; publish();
        }
        adapter.disconnect();
    }
    private static <T> T waitFor(CompletableFuture<T> future, long timeout) throws Exception {
        try { return future.get(timeout, TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { future.cancel(true); throw new TimeoutException("Zeitüberschreitung bei der Kamera."); }
        catch (InterruptedException e) { future.cancel(true); throw e; }
    }
    private static String readable(Throwable ex) {
        while ((ex instanceof ExecutionException || ex instanceof CompletionException) && ex.getCause()!=null)
            ex = ex.getCause();
        return ex.getMessage() == null ? "Kameravorgang fehlgeschlagen" : ex.getMessage();
    }
    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true; epoch++;
            diagnostics.record(Diagnostics.Event.SESSION_CLOSE);
            if (commandResult != null) commandResult.completeExceptionally(new CancellationException("Sitzung beendet"));
            connection = Connection.DISCONNECTED; recording = Recording.UNKNOWN;
            battery = null; seconds = null; batteryAt = -1; storage = Storage.unknown(); captureClock.invalidate(); publish(); observers.clear();
        }
        scheduler.shutdownNow(); commands.shutdownNow(); adapter.close();
    }
}
