package de.markus.aceride.core;

import static de.markus.aceride.core.CameraAdapter.Recording;
import static de.markus.aceride.core.Controller.*;

/** Shared action policy for the activity and Karoo fields. No automatic commands. */
public final class RideControls {
    private RideControls() {}
    public enum Slot { WAKE, CONNECT, START, PAUSE, STOP, POWER_OFF }
    public static Action action(Slot slot, State state) {
        return switch (slot) {
            case WAKE -> Action.WAKE;
            case CONNECT -> state != null && state.connection() == Connection.CONNECTED ? Action.REFRESH : Action.CONNECT;
            case START -> state != null && state.pauseIntent() ? Action.RESUME : Action.START;
            case PAUSE -> Action.PAUSE;
            case STOP -> Action.STOP;
            case POWER_OFF -> Action.POWER_OFF;
        };
    }
    public static boolean enabled(Slot slot, State state, boolean session, boolean selected) {
        if (!session || !selected || state == null || (state.busy() && !state.background())) return false;
        return switch (slot) {
            case PAUSE -> state.recording() == Recording.RECORDING;
            case STOP -> state.power() != Power.OFF_REQUESTED;
            case START -> state.recording() != Recording.RECORDING && state.recording() != Recording.TRANSITION;
            case POWER_OFF -> (state.camera() == null || CameraModels.shutdown(state.camera().model())) && state.connection() == Connection.CONNECTED &&
                (state.recording() == Recording.IDLE || state.recording() == Recording.RECORDING);
            default -> true;
        };
    }
}
