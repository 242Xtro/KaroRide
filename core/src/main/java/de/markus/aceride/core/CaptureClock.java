package de.markus.aceride.core;

import static de.markus.aceride.core.CameraAdapter.*;

/** Time display follows confirmed capture only; never proves recording by itself. */
public final class CaptureClock {
    public record Duration(Long seconds, boolean estimated) {}
    private long anchorAt = -1, anchorSeconds, lastRaw = -1;
    private boolean localAnchor;
    public void invalidate() { anchorAt = -1; lastRaw = -1; localAnchor = false; }
    public void observe(Recording previous, Status value) {
        if (value.recording() == Recording.IDLE) { invalidate(); return; }
        if (value.recording() != Recording.RECORDING) { invalidate(); return; }
        Long raw = value.seconds();
        if (previous != Recording.RECORDING) {
            invalidate();
            if (raw != null && raw > 0) set(raw, value.readStartedAtMs(), false);
            else if (previous == Recording.IDLE) set(0, value.readStartedAtMs(), true);
            // Already recording on reconnect with no timer: total clip age is unknown.
        } else if (raw != null && raw > 0 && raw != lastRaw) {
            set(raw, value.readStartedAtMs(), false);
        } else if (raw != null && raw == 0 && lastRaw > 0) {
            // Could be a new clip or a missing counter; do not extend the old clip.
            invalidate();
        }
        if (raw != null) lastRaw = raw;
    }
    private void set(long seconds, long at, boolean local) {
        anchorAt = at; anchorSeconds = seconds; localAnchor = local;
    }
    public Duration display(Recording shown, long now) {
        if (shown == Recording.IDLE) return new Duration(0L, false);
        if (shown != Recording.RECORDING || anchorAt < 0 || anchorAt > now) return new Duration(null, false);
        long elapsed = (now-anchorAt)/1000;
        return new Duration(anchorSeconds+elapsed, localAnchor || elapsed > 0);
    }
}
