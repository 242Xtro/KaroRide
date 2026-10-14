package de.markus.aceride.core;

import java.util.Locale;
import java.util.Set;
import static de.markus.aceride.core.CameraAdapter.*;

/** Normalizes replies from the pinned SDK. Unknown/saving states never become idle. */
public final class SdkStatusMapper {
    private SdkStatusMapper() {}
    private static final Set<String> VIDEO_MODES = Set.of("VIDEO_NORMAL", "VIDEO_SUPER",
            "VIDEO_SLOW_MOTION", "VIDEO_PURE", "VIDEO_TIMELAPSE", "VIDEO_TIMESHIFT",
            "VIDEO_LOOP_RECORDING", "VIDEO_MOSQUITO");
    private static final Set<String> RECORDING_STATES = Set.of("NORMAL_CAPTURE", "SUPER_NORMAL_CAPTURE",
            "SLOW_MOTION_CAPTURE", "PURE_RECORDING_CAPTURE", "TIMELAPSE_CAPTURE", "TIMESHIFT_CAPTURE",
            "LOOP_RECORDING_CAPTURE", "MOSQUITO_SHOOTING", "HDR_CAPTURE", "BULLET_TIME_CAPTURE",
            "FPV_RECORDING_CAPTURE", "MOVIE_RECORDING_CAPTURE", "SELFIE_RECORDING_CAPTURE", "DASH_CAM");
    private static final Set<String> TRANSITIONS = Set.of("RECORD_SAVE", "PHOTO_SAVE", "STARTING",
            "TRIMING_START", "PHOTO_CANCEL", "RECORD_CANCEL", "STARTLAPSE_SYNTHESIS", "EXPOSURE",
            "PAUSE", "PRE_RECORDING");
    public static Mode mode(String name) {
        if (name == null) return Mode.UNKNOWN;
        if (VIDEO_MODES.contains(name)) return Mode.VIDEO;
        if (name.equals("NONE")) return Mode.UNKNOWN;
        return Mode.OTHER;
    }
    public static Recording recording(String capture, String sub, Mode mode) {
        if (capture == null || sub == null) return Recording.UNKNOWN;
        if (TRANSITIONS.contains(sub) || capture.equals("PRE_RECORD") || capture.equals("PRE_RECORD_STOP"))
            return Recording.TRANSITION;
        if (!Set.of("IDLE", "RUNNING", "RUNNING_STARTED_WITH_PRE_RECORD").contains(sub))
            return Recording.UNKNOWN;
        if (capture.equals("NOT_CAPTURE")) return sub.equals("IDLE") ? Recording.IDLE : Recording.UNKNOWN;
        if (RECORDING_STATES.contains(capture)) return mode == Mode.VIDEO ? Recording.RECORDING : Recording.UNKNOWN;
        return Recording.UNKNOWN;
    }
    public static Integer battery(int level, int scale) {
        if (scale <= 0) return level >= 0 && level <= 100 ? level : null;
        if (level < 0 || level > scale) return null;
        return (int) Math.round((double) level * 100 / scale);
    }
    public static Storage storage(String state, String location, long free, long total, long at) {
        if (!"CAMERA".equals(location) && !"SD".equals(location)) return Storage.unknown();
        StorageState mapped = switch (state == null ? "" : state) {
            case "PASS" -> StorageState.AVAILABLE;
            case "NO_SPACE" -> StorageState.FULL;
            case "NO_CARD" -> StorageState.NO_CARD;
            case "WP_CARD" -> StorageState.READ_ONLY;
            case "INVALID_FORMAT", "OTHER_ERROR" -> StorageState.ERROR;
            default -> StorageState.UNKNOWN;
        };
        return new Storage(mapped, free, total, at);
    }
    /** SDK 2.1.5 Demo passes the last six serial characters, not the BLE display name. */
    public static String wakeIdentifier(String serial) {
        if (serial == null || !serial.matches("[A-Za-z0-9]{6,}"))
            throw new IllegalArgumentException("Aufweckkennung fehlt. Kamera eingeschaltet erneut verbinden.");
        return serial.substring(serial.length() - 6).toUpperCase(Locale.ROOT);
    }
}
