package de.markus.aceride.core;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static de.markus.aceride.core.CameraAdapter.*;

/** Presentation contracts; these JVM tests do not claim Android preferences/dialog hardware tests. */
final class LanguageTest {
    private static int passed;
    private static void check(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
    private static void test(String name, Runnable test) { test.run(); passed++; System.out.println("PASS " + name); }
    static int run() throws Exception {
        test("Sprache ohne gespeicherte Auswahl ist Englisch", () -> check(Language.fromCode(null) == Language.ENGLISH, "default"));
        test("Ungültige gespeicherte Sprache fällt auf Englisch zurück", () -> check(Language.fromCode("") == Language.ENGLISH && Language.fromCode("fr") == Language.ENGLISH, "invalid preference"));
        test("Sprachcodes behalten die explizite Auswahl beim erneuten Lesen", () -> {
            for (Language language : Language.values()) check(Language.fromCode(language.code()) == language, "round trip");
        });
        test("Englische Speichermenge verwendet Dezimalpunkt", () -> check(DisplayValues.bytes(1021937451008L, Language.ENGLISH).equals("1021.9 GB"), "English GB"));
        test("Deutsche Speichermenge verwendet Dezimalkomma", () -> check(DisplayValues.bytes(1021937451008L, Language.GERMAN).equals("1021,9 GB"), "German GB"));
        test("Kleine Speichermengen bleiben in beiden Sprachen lesbar", () -> check(DisplayValues.bytes(500000,Language.ENGLISH).equals("0.5 MB") && DisplayValues.bytes(500000,Language.GERMAN).equals("0,5 MB"), "small free space"));
        test("Fehlender Speicher bleibt unbekannt in beiden Sprachen", () -> check(DisplayValues.storage(null,Language.ENGLISH).equals("Free: ?") && DisplayValues.storage(Storage.unknown(),Language.GERMAN).equals("Frei: ?"), "unknown is not zero"));
        test("Kapazität und SD-Fehlerzustände werden getrennt übersetzt", () -> {
            check(DisplayValues.storage(new Storage(StorageState.AVAILABLE,1500000000L,2000000000L,0),Language.ENGLISH).equals("Free: 1.5 GB"), "valid free bytes");
            check(DisplayValues.storage(new Storage(StorageState.FULL,null,null,0),Language.ENGLISH).equals("Storage full"), "full");
            check(DisplayValues.storage(new Storage(StorageState.NO_CARD,null,null,0),Language.ENGLISH).equals("No SD card"), "no card");
            check(DisplayValues.storage(new Storage(StorageState.READ_ONLY,null,null,0),Language.ENGLISH).equals("SD read-only"), "locked");
            check(DisplayValues.storage(new Storage(StorageState.ERROR,null,null,0),Language.ENGLISH).equals("SD error"), "error");
        });
        test("Ungültige Speicherwerte werden auch auf Englisch nicht erfunden", () -> check(DisplayValues.storage(new Storage(StorageState.AVAILABLE,10L,5L,0),Language.ENGLISH).equals("Free: ?"), "invalid capacity"));
        test("Sprachformatierung verändert keine globale JVM-Sprache", () -> {
            Locale before=Locale.getDefault();
            for (Language language : Language.values()) DisplayValues.bytes(1230000000L,language);
            check(Locale.getDefault().equals(before), "global locale unchanged");
        });
        test("Bisherige deutsche Anzeige bleibt mit explizitem Deutsch identisch", () -> {
            Storage value=new Storage(StorageState.AVAILABLE,1230000000L,2000000000L,0);
            check(DisplayValues.storage(value).equals(DisplayValues.storage(value,Language.GERMAN)), "legacy display");
        });
        // A simulated live recording does not send commands merely to format translated telemetry.
        AtomicLong now=new AtomicLong(100);
        SimulatedCamera camera=new SimulatedCamera(now::get);
        try (Controller ctl=new Controller(camera,now::get,new Controller.Timing(500,1500,100000,100,20,100000),false,p -> {})) {
            ctl.restore(new Camera("test-language-camera","Ace Pro test",CameraModels.ACE));
            ctl.submit(Controller.Action.START).get(2,TimeUnit.SECONDS);
            int starts=camera.starts,stops=camera.stops,connects=camera.connects,wakes=camera.wakes;
            Controller.State before=ctl.state();
            test("Zweisprachige Anzeige sendet während REC keinen Kamerabefehl", () -> {
                for (Language language : Language.values()) {
                    DisplayValues.storage(before.storage(),language);DisplayValues.duration(before.seconds(),before.durationEstimated());
                }
                check(ctl.state().recording()==Recording.RECORDING && camera.starts==starts && camera.stops==stops && camera.connects==connects && camera.wakes==wakes,"display only");
                check(ctl.selectedCamera()==before.camera() && ctl.state().pauseIntent()==before.pauseIntent(),"selection and pause retained");
            });
        }
        return passed;
    }
}
