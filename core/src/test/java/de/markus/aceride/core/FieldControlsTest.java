package de.markus.aceride.core;

import static de.markus.aceride.core.Controller.*;
import static de.markus.aceride.core.CameraAdapter.*;

final class FieldControlsTest {
    private static int passed;
    static State state(Recording rec, Connection link, boolean pause, boolean busy, boolean background, Power power) {
        return new State(link, rec, Recording.RECORDING, 0, 75, 4L, pause, busy, "", Mode.VIDEO, power, background);
    }
    static State ready(Recording rec) { return state(rec, Connection.CONNECTED, false, false, false, Power.ON); }
    static void check(boolean value) { if (!value) throw new AssertionError("Start/Stopp-Datenfeld"); }
    static void test(String name, Runnable work) { work.run(); passed++; System.out.println("PASS "+name); }
    static int run() {
        test("Start/Stopp-Feld: bestätigte Aufnahme bietet nur Stopp", () -> {
            var d=FieldControls.decide(ready(Recording.RECORDING),true,true,false);
            check(d.action()==Action.STOP && d.enabled() && !d.openApp());
        });
        test("Start/Stopp-Feld: Leerlauf startet, lokale Pause setzt fort", () -> {
            check(FieldControls.decide(ready(Recording.IDLE),true,true,false).action()==Action.START);
            check(FieldControls.decide(state(Recording.IDLE,Connection.CONNECTED,true,false,false,Power.ON),true,true,false).action()==Action.RESUME);
        });
        test("Start/Stopp-Feld: unbekannter verbundener Zustand fragt nur Status", () -> {
            for (Recording r : new Recording[]{Recording.UNKNOWN,Recording.TRANSITION})
                check(FieldControls.decide(ready(r),true,true,false).action()==Action.REFRESH);
        });
        test("Start/Stopp-Feld: Funkverlust verbindet ohne Capture-Neustart", () -> {
            check(FieldControls.decide(state(Recording.UNKNOWN,Connection.DISCONNECTED,false,false,false,Power.UNKNOWN),true,true,false).action()==Action.CONNECT);
        });
        test("Start/Stopp-Feld: nach Aus nur bewusstes Aufwecken", () -> {
            check(FieldControls.decide(state(Recording.UNKNOWN,Connection.DISCONNECTED,false,false,false,Power.OFF_REQUESTED),true,true,false).action()==Action.WAKE);
        });
        test("Start/Stopp-Feld: fehlende Sitzung und Auswahl öffnen Einrichtung", () -> {
            var selected=FieldControls.decide(null,false,true,false);
            check(selected.action()==Action.CONNECT && selected.enabled() && selected.openApp());
            var missing=FieldControls.decide(null,false,false,false);
            check(missing.action()==null && missing.enabled() && missing.openApp());
        });
        test("Start/Stopp-Feld: Capture-Befehl sperrt, Hintergrundstatus bleibt unterbrechbar", () -> {
            check(!FieldControls.decide(state(Recording.RECORDING,Connection.CONNECTED,false,true,false,Power.ON),true,true,false).enabled());
            check(FieldControls.decide(state(Recording.RECORDING,Connection.CONNECTED,false,true,true,Power.ON),true,true,false).action()==Action.STOP);
        });
        test("Start/Stopp-Feld: Profilvorschau löst weder Kamera noch App aus", () -> {
            for(boolean session : new boolean[]{false,true}) for(boolean selected : new boolean[]{false,true}) {
                var d=FieldControls.decide(ready(Recording.RECORDING),session,selected,true);
                check(!d.enabled() && !d.openApp() && d.action()==null);
            }
        });
        test("Start/Stopp-Feld: echte Controller-Aktion startet und stoppt genau einen Clip", () -> {
            java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(100);
            SimulatedCamera camera = new SimulatedCamera(time::get);
            try (Controller ctl = new Controller(camera,time::get,new Timing(100,30,30,100,10000,1000),false,p -> {})) {
                ctl.restore(new Camera("AA","Ace Pro TEST","ACE_PRO_1")); ctl.submit(Action.CONNECT).join();
                ctl.submit(FieldControls.decide(ctl.state(),true,true,false).action()).join();
                check(ctl.state().recording()==Recording.RECORDING && camera.starts==1 && camera.clipFiles==1);
                ctl.submit(FieldControls.decide(ctl.state(),true,true,false).action()).join();
                check(ctl.state().recording()==Recording.IDLE && camera.stops==1 && camera.starts==1);
            }
        });
        test("Start/Stopp-Feld: Wiederverbindung gleicht laufenden Clip ab und startet nicht neu", () -> {
            java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(100);
            SimulatedCamera camera = new SimulatedCamera(time::get);
            try (Controller ctl = new Controller(camera,time::get,new Timing(100,30,30,100,10000,1000),false,p -> {})) {
                ctl.restore(new Camera("AA","Ace Pro TEST","ACE_PRO_1")); ctl.submit(Action.START).join(); camera.drop();
                var reconnect=FieldControls.decide(ctl.state(),true,true,false);
                check(reconnect.action()==Action.CONNECT && ctl.state().recording()==Recording.UNKNOWN);
                ctl.submit(reconnect.action()).join();
                check(camera.starts==1 && camera.clipFiles==1 && ctl.state().recording()==Recording.RECORDING);
                check(FieldControls.decide(ctl.state(),true,true,false).action()==Action.STOP);
            }
        });
        return passed;
    }
}
