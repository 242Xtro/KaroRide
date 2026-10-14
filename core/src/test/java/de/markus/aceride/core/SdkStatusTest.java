package de.markus.aceride.core;

import static de.markus.aceride.core.CameraAdapter.*;

final class SdkStatusTest {
    private static int passed;
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static void test(String name, Runnable body) { body.run(); passed++; System.out.println("PASS SDK-Normalisierung: " + name); }
    static int run() {
        test("Video bleibt Video, Foto und unsteuerbare Modi sind gesperrt", () -> {
            check(SdkStatusMapper.mode("VIDEO_NORMAL") == Mode.VIDEO, "normal video");
            check(SdkStatusMapper.mode("VIDEO_PURE") == Mode.VIDEO, "pure video");
            check(SdkStatusMapper.mode("PHOTO_NORMAL") == Mode.OTHER, "photo");
            check(SdkStatusMapper.mode("VIDEO_LIVE") != Mode.VIDEO, "live unsupported");
            check(SdkStatusMapper.mode("VIDEO_HDR") != Mode.VIDEO, "SDK lists HDR unsupported");
            check(SdkStatusMapper.mode(null) == Mode.UNKNOWN, "missing mode");
        });
        test("Nur echter Leerlauf bestätigt Stopp", () -> {
            check(SdkStatusMapper.recording("NOT_CAPTURE", "IDLE", Mode.VIDEO) == Recording.IDLE, "idle");
            check(SdkStatusMapper.recording("NOT_CAPTURE", "RUNNING", Mode.VIDEO) == Recording.UNKNOWN, "conflict");
        });
        test("Normale und Pure-Video-Rückmeldungen bestätigen Aufnahme", () -> {
            check(SdkStatusMapper.recording("NORMAL_CAPTURE", "RUNNING", Mode.VIDEO) == Recording.RECORDING, "recording");
            check(SdkStatusMapper.recording("PURE_RECORDING_CAPTURE", "IDLE", Mode.VIDEO) == Recording.RECORDING, "older firmware substatus");
        });
        test("Speichern und Starten sind Übergänge", () -> {
            check(SdkStatusMapper.recording("NOT_CAPTURE", "RECORD_SAVE", Mode.VIDEO) == Recording.TRANSITION, "saving");
            check(SdkStatusMapper.recording("NORMAL_CAPTURE", "STARTING", Mode.VIDEO) == Recording.TRANSITION, "starting");
        });
        test("Unbekannte oder widersprüchliche Zustände bleiben unbekannt", () -> {
            check(SdkStatusMapper.recording("FUTURE_STATUS", "IDLE", Mode.VIDEO) == Recording.UNKNOWN, "new enum");
            check(SdkStatusMapper.recording("NORMAL_CAPTURE", "FUTURE_SUB", Mode.VIDEO) == Recording.UNKNOWN, "new substatus");
            check(SdkStatusMapper.recording("NORMAL_CAPTURE", "RUNNING", Mode.OTHER) == Recording.UNKNOWN, "mode conflict");
        });
        test("Pre-Recording und kamerainterne Pause gelten nicht als Leerlauf", () -> {
            check(SdkStatusMapper.recording("PRE_RECORD", "IDLE", Mode.VIDEO) == Recording.TRANSITION, "prerecord");
            check(SdkStatusMapper.recording("NORMAL_CAPTURE", "PAUSE", Mode.VIDEO) == Recording.TRANSITION, "native pause");
        });
        test("Akku wird skaliert und ungültige Werte verworfen", () -> {
            check(SdkStatusMapper.battery(3, 4) == 75, "battery scale");
            check(SdkStatusMapper.battery(101, 100) == null, "out of range");
            check(SdkStatusMapper.battery(67, 0) == 67, "documented percentage without scale");
            check(SdkStatusMapper.battery(101, 0) == null, "invalid unscaled percentage");
            check(SdkStatusMapper.battery(67, -1) == 67, "documented unscaled percent fallback");
        });
        test("Aufweckkennung verwendet sechs Serienzeichen und lehnt ungültige Daten ab", () -> {
            check(SdkStatusMapper.wakeIdentifier("TEST00ABC123").equals("ABC123"), "serial suffix");
            boolean rejected = false;
            try { SdkStatusMapper.wakeIdentifier("short"); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "short serial");
        });
        return passed;
    }
}
