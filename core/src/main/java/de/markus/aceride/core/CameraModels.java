package de.markus.aceride.core;

import java.util.Locale;

/** Explicit model capabilities of SDK 2.1.5, not a hardware compatibility claim. */
public final class CameraModels {
    public static final String ACE = "ACE_PRO_1", GO = "GO_3S";
    private CameraModels() {}
    public static boolean supported(String model) { return ACE.equals(model) || GO.equals(model); }
    public static String label(String model) { return GO.equals(model) ? "GO 3S" : "Ace Pro"; }
    public static boolean shutdown(String model) { return ACE.equals(model); }
    public static boolean needsAdvertising(String model) { return ACE.equals(model); }
    /** Discovery hint only. The actual SDK model must match before any capture. */
    public static String discover(String name) {
        if (name == null) return null;
        String normalized = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.startsWith("INSTA360")) normalized = normalized.substring(8);
        if (normalized.startsWith("GO3S")) return GO;
        if (normalized.startsWith("ACEPRO") && !normalized.startsWith("ACEPRO2")) return ACE;
        return null;
    }
}
