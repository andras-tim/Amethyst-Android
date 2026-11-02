package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.MotionEvent;

import net.kdt.pojavlaunch.customcontrols.ControlLayout;

/**
 * Hides the touch controls while a gamepad is in use, and brings them back at the next touch. It restores only what it
 * hid: controls the player turned off stay off.
 */
public class GamepadTouchControls {
    private static final int[] AXES = {
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS
    };
    // Above the rest position noise of a worn stick, so an idle gamepad does not hide the controls after a touch
    private static final float DELIBERATE_INPUT = 0.5f;

    private final ControlLayout mLayout;
    private boolean mHidden;

    public GamepadTouchControls(ControlLayout layout) {
        mLayout = layout;
    }

    public void onGamepadInput() {
        if (!mLayout.areControlVisible()) return;
        mHidden = true;
        mLayout.setControlVisible(false);
    }

    public void onGamepadInput(MotionEvent event) {
        for (int axis : AXES) {
            if (Math.abs(event.getAxisValue(axis)) > DELIBERATE_INPUT) {
                onGamepadInput();
                return;
            }
        }
    }

    public void onTouch() {
        if (!mHidden) return;
        mHidden = false;
        mLayout.setControlVisible(true);
    }
}
