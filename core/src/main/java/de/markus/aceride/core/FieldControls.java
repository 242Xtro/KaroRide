package de.markus.aceride.core;

import static de.markus.aceride.core.Controller.*;
import static de.markus.aceride.core.CameraAdapter.Recording;

/** One deliberate action for a compact ride field. Unknown state never starts a clip. */
public final class FieldControls {
    private FieldControls() {}
    public record Decision(Action action, boolean enabled, boolean openApp) {}
    public static Decision decide(State state, boolean session, boolean selected, boolean preview) {
        if (preview) return new Decision(null, false, false);
        if (!selected) return new Decision(null, true, true);
        if (!session || state == null) return new Decision(Action.CONNECT, true, true);
        if (state.busy() && !state.background()) return new Decision(null, false, false);
        Action action;
        if (state.power() == Power.OFF_REQUESTED) action = Action.WAKE;
        else if (state.connection() != Connection.CONNECTED) action = Action.CONNECT;
        else if (state.recording() == Recording.RECORDING) action = Action.STOP;
        else if (state.recording() == Recording.IDLE) action = state.pauseIntent() ? Action.RESUME : Action.START;
        else action = Action.REFRESH;
        return new Decision(action, true, false);
    }
}
