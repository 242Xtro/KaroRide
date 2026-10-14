package de.markus.aceride.core;

import java.util.*;

/** Simulated teardown port; does not stand in for a real SDK/GATT stack. */
final class QuietReleaseTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("SDK-Freigabereihenfolge"); }
    static int run() {
        List<String> steps = new ArrayList<>();
        QuietRelease.release(new QuietRelease.Port() {
            public void stopAdvertising() { steps.add("stop"); }
            public void closeGatt() { steps.add("gatt"); }
            public void releaseDevice() { steps.add("release"); }
        }, Diagnostics.NONE);
        check(steps.equals(List.of("stop","gatt","release")));
        System.out.println("PASS Aus-Freigabe: Broadcast stoppen und lokales GATT schließen vor SDK-release");
        steps.clear(); List<Diagnostics.Event> events = new ArrayList<>();
        QuietRelease.release(new QuietRelease.Port() {
            public void stopAdvertising() { steps.add("stop"); throw new IllegalStateException("synthetic-secret"); }
            public void closeGatt() { steps.add("gatt"); throw new IllegalStateException("synthetic-secret"); }
            public void releaseDevice() { steps.add("release"); }
        }, (event, values) -> events.add(event));
        check(steps.equals(List.of("stop","gatt","release")));
        check(events.contains(Diagnostics.Event.WAKE_STOP_ERROR) && events.contains(Diagnostics.Event.SDK_RELEASE_ERROR));
        System.out.println("PASS Aus-Freigabe: Teilfehler geben übrige Ressourcen frei");
        return 2;
    }
}
