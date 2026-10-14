package de.markus.aceride.core;

import static de.markus.aceride.core.CameraAdapter.*;

final class TelemetryTest {
    private static int count;
    private static void check(boolean value) { if (!value) throw new AssertionError("Mengenwerte/Zeit"); }
    private static void test(String name, Runnable body) { body.run(); count++; System.out.println("PASS Mengenwerte: " + name); }
    static int run() {
        test("Dokumentierter Akku-Prozentwert ohne Skala einschließlich leer/voll", () -> {
            check(SdkStatusMapper.battery(0,0)==0 && SdkStatusMapper.battery(100,0)==100 && SdkStatusMapper.battery(67,0)==67);
            check(SdkStatusMapper.battery(67,-1)==67);
            check(SdkStatusMapper.battery(-1,0)==null && SdkStatusMapper.battery(101,0)==null);
            check(SdkStatusMapper.battery(2,3)==67);
        });
        test("SD-/Kameraspeicher wird gewählt, interner/Reader-Speicher nicht", () -> {
            check(SdkStatusMapper.storage("PASS","CAMERA",100,200,0).freeBytes()==100);
            check(SdkStatusMapper.storage("PASS","SD",100,200,0).state()==StorageState.AVAILABLE);
            check(SdkStatusMapper.storage("PASS","INNER",100,200,0).state()==StorageState.UNKNOWN);
            check(SdkStatusMapper.storage("PASS","READER",100,200,0).state()==StorageState.UNKNOWN);
        });
        test("Ungültige Speichergrößen werden nicht als verfügbar angezeigt", () -> {
            for (Storage s : new Storage[]{new Storage(StorageState.AVAILABLE,-1L,100L,0),
                    new Storage(StorageState.AVAILABLE,200L,100L,0),new Storage(StorageState.AVAILABLE,0L,0L,0)})
                check(s.state()==StorageState.UNKNOWN && s.freeBytes()==null);
        });
        test("Volle/fehlende/fehlerhafte SD-Karte hat einen eigenen Zustand", () -> {
            check(DisplayValues.storage(SdkStatusMapper.storage("NO_SPACE","CAMERA",0,200,0)).equals("SD voll"));
            check(DisplayValues.storage(SdkStatusMapper.storage("NO_CARD","CAMERA",0,0,0)).equals("Keine SD"));
            check(DisplayValues.storage(SdkStatusMapper.storage("INVALID_FORMAT","CAMERA",0,0,0)).equals("SD-Fehler"));
            check(DisplayValues.storage(SdkStatusMapper.storage("WP_CARD","CAMERA",100,200,0)).equals("SD gesperrt"));
        });
        test("Freier Speicher nutzt Bytewerte und deutsche dezimale GB/MB", () -> {
            check(DisplayValues.bytes(118_300_000_000L).equals("118,3 GB"));
            check(DisplayValues.bytes(3_000_000_000_000L).equals("3000,0 GB"));
            check(DisplayValues.bytes(500_000_000L).equals("500 MB"));
            check(DisplayValues.storage(Storage.unknown()).equals("Frei: ?"));
        });
        test("Zeitanzeige kann Stunden und kennzeichnet lokale Schätzung", () -> {
            check(DisplayValues.duration(3723L,false).equals("Clip: 01:02:03"));
            check(DisplayValues.duration(42L,true).equals("Clip: ~00:42"));
            check(DisplayValues.duration(null,false).equals("Clip: ?"));
        });
        test("Kein Timer aus unbestätigter Aufnahme, auch nicht aus einer Sekundenzahl", () -> {
            CaptureClock clock=new CaptureClock();
            clock.observe(Recording.IDLE,new Status(Recording.UNKNOWN,Mode.VIDEO,75,10L,0));
            check(clock.display(Recording.UNKNOWN,5000).seconds()==null);
        });
        test("Nicht fortschreitender SDK-Zähler friert bestätigte Aufnahme nicht ein", () -> {
            CaptureClock clock=new CaptureClock();
            clock.observe(Recording.IDLE,new Status(Recording.RECORDING,Mode.VIDEO,75,0L,100));
            clock.observe(Recording.RECORDING,new Status(Recording.RECORDING,Mode.VIDEO,75,0L,3100));
            check(clock.display(Recording.RECORDING,5100).seconds()==5 && clock.display(Recording.RECORDING,5100).estimated());
        });
        test("Vor Verbindung bereits laufender Clip erhält keine erfundene Gesamtdauer", () -> {
            CaptureClock clock=new CaptureClock();
            clock.observe(Recording.UNKNOWN,new Status(Recording.RECORDING,Mode.VIDEO,75,0L,100));
            check(clock.display(Recording.RECORDING,5100).seconds()==null);
        });
        test("Echter SDK-Zähler wird übernommen, Rücksprung auf null wird unklar", () -> {
            CaptureClock clock=new CaptureClock();
            clock.observe(Recording.UNKNOWN,new Status(Recording.RECORDING,Mode.VIDEO,75,30L,100));
            check(clock.display(Recording.RECORDING,100).seconds()==30 && !clock.display(Recording.RECORDING,100).estimated());
            clock.observe(Recording.RECORDING,new Status(Recording.RECORDING,Mode.VIDEO,75,0L,3100));
            check(clock.display(Recording.RECORDING,3100).seconds()==null);
        });
        return count;
    }
}
