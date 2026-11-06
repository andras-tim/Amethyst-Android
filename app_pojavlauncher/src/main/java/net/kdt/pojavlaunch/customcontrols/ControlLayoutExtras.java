package net.kdt.pojavlaunch.customcontrols;

import static org.lwjgl.glfw.CallbackBridge.isGrabbing;

import android.view.View;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;

/**
 * The fork's behaviors of the control layout, kept out of the upstream {@link ControlLayout}: the extended level of
 * the controls toggle, hiding the controls while the game loads ({@code hideUntilLog}), and fading the buttons near a
 * finger that drags.
 */
public final class ControlLayoutExtras {
    private final ControlLayout mLayout;
    private boolean mExpanded = false;
    private volatile boolean mLoading = false;
    private volatile String mHideUntilLog;
    private ControlInterface mDragging;
    private final Logger.eventLogListener mLoadListener;

    ControlLayoutExtras(ControlLayout layout) {
        mLayout = layout;
        mLoadListener = text -> {
            String hideUntilLog = mHideUntilLog;
            if (hideUntilLog != null && text.contains(hideUntilLog)) mLayout.post(this::endLoading);
        };
    }

    /** The game is still loading: no control shows until its log names the end of loading (see hideUntilLog). */
    void startLoading(CustomControls controls, boolean modifiable) {
        Logger.removeLogListener(mLoadListener);
        mHideUntilLog = controls.hideUntilLog;
        mLoading = !modifiable && mHideUntilLog != null && !mHideUntilLog.isEmpty();
        if (mLoading) Logger.addLogListener(mLoadListener);
    }

    /** Shows the controls when loading ends, or at the first touch on the game if the log line never came. */
    public void endLoading() {
        if (!mLoading) return;
        mLoading = false;
        Logger.removeLogListener(mLoadListener);
        mLayout.setControlVisible(mLayout.areControlVisible());
    }

    /**
     * With extended buttons in the layout a tap switches between everything and the buttons without them, and brings
     * hidden controls back. False: no extended buttons, the toggle shows and hides the controls as upstream does.
     */
    boolean toggleControlVisible() {
        if (!hasExtendedButtons()) return false;
        mExpanded = mLayout.areControlVisible() && !mExpanded;
        mLayout.setControlVisible(true);
        return true;
    }

    public void hideAllControls() {
        mLayout.setControlVisible(false);
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    private boolean hasExtendedButtons() {
        for (ControlInterface button : mLayout.getButtonChildren()) if (button.getProperties().extended) return true;
        return false;
    }

    /**
     * Whether a button shows now. The game and menu flags apply to every button; hiding, the extended level and the
     * soft keyboard only to the buttons the controls toggle can hide.
     */
    public boolean isButtonShown(ControlInterface button) {
        if (mLoading) return false;
        ControlData data = button.getProperties();
        boolean grabbing = isGrabbing();
        if (!((data.displayInGame && grabbing) || (data.displayInMenu && !grabbing))) return false;
        if (!data.isHideable) return true;
        if (!mLayout.areControlVisible() || mLayout.isHiddenByIme(data)) return false;
        return !data.extended || mExpanded;
    }

    /**
     * While a finger drags on a control (the joystick, a pass-through button), the other buttons on its side of the
     * screen fade to the layout's dragFadeOpacity and stop taking touches: the finger may cross them, and they stay
     * visible for when it lifts. null ends the drag.
     */
    public void setDragging(ControlInterface button) {
        Float opacity = mLayout.getLayout() == null ? null : mLayout.getLayout().dragFadeOpacity;
        if (opacity == null || mDragging == button) return;
        mDragging = button;
        for (ControlInterface child : mLayout.getButtonChildren()) {
            boolean faded = button != null && child != button && isOnDragSide(child);
            child.getControlView().setAlpha(faded ? opacity : 1f);
            child.getControlView().setEnabled(!faded);
        }
    }

    private boolean isOnDragSide(ControlInterface button) {
        float middle = mLayout.getWidth() / 2f;
        View drag = mDragging.getControlView(), view = button.getControlView();
        return (drag.getX() + drag.getWidth() / 2f > middle) == (view.getX() + view.getWidth() / 2f > middle);
    }
}
