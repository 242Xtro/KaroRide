package de.markus.aceride.core;

import static de.markus.aceride.core.CameraAdapter.*;
import static de.markus.aceride.core.Controller.*;
import static de.markus.aceride.core.RideControls.*;

/** Tests actual shared UI action mapping, including stale/unknown capture. */
final class RideControlsTest {
    static State state(Recording rec, boolean pause, boolean busy, Connection link) {
        return new State(link, rec, rec, 0, 75, 4L, pause, busy, "", Mode.VIDEO, Power.ON, false);
    }
    static void check(boolean value) { if (!value) throw new AssertionError("Fahrtasten-Zuordnung"); }
    static int run() {
        State paused = state(Recording.IDLE, true, false, Connection.CONNECTED);
        check(action(Slot.START, paused) == Action.RESUME);
        check(action(Slot.START, state(Recording.IDLE, false, false, Connection.CONNECTED)) == Action.START);
        System.out.println("PASS Fahrtasten: Fortsetzen nur bei lokaler Pause");
        for (Slot slot : Slot.values()) check(!enabled(slot, state(Recording.IDLE, false, true, Connection.CONNECTED), true, true));
        System.out.println("PASS Fahrtasten: laufender Befehl sperrt schnelle Mehrfachberührung");
        State unknown = state(Recording.UNKNOWN, true, false, Connection.DISCONNECTED);
        check(!enabled(Slot.POWER_OFF, unknown, true, true) && !enabled(Slot.PAUSE, unknown, true, true));
        check(action(Slot.STOP, unknown) == Action.STOP && action(Slot.WAKE, unknown) == Action.WAKE);
        System.out.println("PASS Fahrtasten: Funkabbruch wird nicht als gestoppte Aufnahme behandelt");
        check(action(Slot.CONNECT, unknown) == Action.CONNECT && action(Slot.CONNECT, paused) == Action.REFRESH);
        System.out.println("PASS Fahrtasten: Verbinden wird bei bestehender Verbindung zur Statusprüfung");
        State off = new State(Connection.DISCONNECTED, Recording.UNKNOWN, Recording.IDLE, 0, null, null,
            false, false, "", Mode.UNKNOWN, Power.OFF_REQUESTED, false);
        check(!enabled(Slot.POWER_OFF, off, true, true) && !enabled(Slot.STOP, off, true, true));
        check(enabled(Slot.WAKE, off, true, true) && enabled(Slot.CONNECT, off, true, true));
        System.out.println("PASS Fahrtasten: nach Aus bleiben bewusstes Verbinden und Aufwecken erreichbar");
        return 5;
    }
}
