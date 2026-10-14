package de.markus.aceride.core;

import java.util.Locale;
import static de.markus.aceride.core.CameraAdapter.*;

/** Quantity labels with explicit app locale. GB/MB are decimal; ~ marks local timing. */
public final class DisplayValues {
    private DisplayValues() {}
    public static String bytes(long value) {
        return bytes(value, Language.GERMAN);
    }
    public static String bytes(long value, Language language) {
        if (value >= 1_000_000_000L) return String.format(language.locale(), "%.1f GB", value/1_000_000_000d);
        if (value >= 1_000_000L) return String.format(language.locale(), "%.0f MB", value/1_000_000d);
        if (value > 0) return String.format(language.locale(), "%.1f MB", value/1_000_000d);
        return "0 MB";
    }
    public static String storage(Storage value) {
        return storage(value, Language.GERMAN);
    }
    public static String storage(Storage value, Language language) {
        boolean de = language == Language.GERMAN;
        String free = de ? "Frei: " : "Free: ";
        if (value == null) return free + "?";
        return switch (value.state()) {
            case AVAILABLE -> value.freeBytes() == null ? free + "?" : free + bytes(value.freeBytes(), language);
            case FULL -> de ? "SD voll" : "Storage full";
            case NO_CARD -> de ? "Keine SD" : "No SD card";
            case READ_ONLY -> de ? "SD gesperrt" : "SD read-only";
            case ERROR -> de ? "SD-Fehler" : "SD error";
            default -> free + "?";
        };
    }
    public static String duration(Long seconds, boolean estimated) {
        if (seconds == null || seconds < 0) return "Clip: ?";
        String time = seconds >= 3600
                ? String.format(Locale.GERMANY, "%02d:%02d:%02d", seconds/3600, (seconds/60)%60, seconds%60)
                : String.format(Locale.GERMANY, "%02d:%02d", seconds/60, seconds%60);
        return "Clip: " + (estimated ? "~" : "") + time;
    }
}
