package net.kdt.pojavlaunch.customcontrols.buttons;

import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;

import org.lwjgl.glfw.CallbackBridge;

/**
 * The fork's behaviors of a control button, kept out of the upstream {@link ControlButton}. Each one is switched on
 * and tuned by fields of the layout, so a layout changes them without a new launcher: the on state as a ring
 * ({@code activeStrokeColor}), a long press key ({@code longPressKeycode}), and the look joystick
 * ({@code lookJoystick}) with the fading of the other buttons while it is held.
 */
final class ControlButtonExtras {
    /** Hides every control, the controls toggle brings them back; far from upstream's codes, which count down from -1 */
    static final int SPECIALBTN_HIDECONTROLS = -101;

    private final ControlButton mButton;
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mLongPressed;
    private boolean mDragging;
    private float mDownX, mDownY;
    private float mLookX, mLookY;
    private long mLookFrameNanos;

    private final Runnable mLookFrame = new Runnable() {
        @Override
        public void run() {
            if (!mDragging) return;
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - mLookFrameNanos) / 1e9f);
            mLookFrameNanos = now;
            // As the movement joystick: past the deadzone the drag snaps to one of the directions and the camera turns
            // that way at one fixed speed, however far the finger goes
            ControlData p = props();
            if (Tools.pxToDp((float) Math.hypot(mLookX, mLookY)) > or(p.lookDeadzone, 25)) {
                double step = 2 * Math.PI / (p.lookDirections > 0 ? p.lookDirections : 8);
                double angle = Math.round(Math.atan2(mLookY, mLookX) / step) * step;
                float speed = or(p.lookSpeed, 400);
                CallbackBridge.mouseX += (float) (Math.cos(angle) * speed * dt);
                CallbackBridge.mouseY += (float) (Math.sin(angle) * speed * dt * or(p.lookVerticalFactor, 1));
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
            }
            mButton.postOnAnimation(this);
        }
    };
    private final Runnable mSendLongPress = () -> {
        mLongPressed = true;
        sendKey(props().longPressKeycode, true);
        sendKey(props().longPressKeycode, false);
    };

    ControlButtonExtras(ControlButton button) {
        mButton = button;
    }

    private static float or(float value, float fallback) {
        return value > 0 ? value : fallback;
    }

    private ControlData props() {
        return mButton.getProperties();
    }

    private ControlLayout layout() {
        return mButton.getControlLayoutParent();
    }

    /** Draws the on state as a ring when the layout asks for one, the press with the press paint; false: not handled. */
    boolean draw(Canvas canvas, float radius, Paint pressPaint) {
        if (props().activeStrokeColor == 0) return false;
        if (isOn()) {
            float width = Tools.dpToPx(or(props().activeStrokeWidth, 1.5f));
            mRingPaint.setStyle(Paint.Style.STROKE);
            mRingPaint.setStrokeWidth(width);
            mRingPaint.setColor(props().activeStrokeColor);
            float inset = width / 2;
            float r = Math.max(0, radius - inset);
            canvas.drawRoundRect(inset, inset, mButton.getWidth() - inset, mButton.getHeight() - inset, r, r, mRingPaint);
        } else if (mButton.isActivated()) {
            canvas.drawRoundRect(0, 0, mButton.getWidth(), mButton.getHeight(), radius, radius, pressPaint);
        }
        return true;
    }

    /** On: a toggle that is down, the controls toggle while all controls show, the virtual mouse while it shows. */
    private boolean isOn() {
        if (props().isToggle) return mButton.mIsToggled;
        if (props().containsKeycode(ControlData.SPECIALBTN_TOGGLECTRL)) {
            return mButton.getParent() instanceof ControlLayout && layout().areControlVisible() && layout().extras().isExpanded();
        }
        if (props().containsKeycode(ControlData.SPECIALBTN_VIRTUALMOUSE)) return MainActivity.isMouseShown();
        return false;
    }

    /** A faded button (see {@link ControlLayoutExtras#setDragging}) lets the touch through to the game. */
    boolean ignoresTouch() {
        return !mButton.isEnabled() && !layout().getModifiable();
    }

    boolean hasLongPress() {
        return props().longPressKeycode != 0 && !props().isToggle && !layout().getModifiable();
    }

    /**
     * A button with a long press key sends its keys as a tap on release, or the long press key once it is held; so a
     * tap and a hold never both fire.
     */
    boolean handleLongPress(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                mLongPressed = false;
                mButton.setActivated(true);
                mButton.postDelayed(mSendLongPress, props().longPressTime > 0 ? props().longPressTime : ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                mButton.removeCallbacks(mSendLongPress);
                if (!mLongPressed && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
                    mButton.sendKeyPresses(true);
                    mButton.sendKeyPresses(false);
                }
                mButton.setActivated(false);
                break;
            default:
                break;
        }
        return true;
    }

    private void sendKey(int keycode, boolean isDown) {
        if (keycode >= GLFW_KEY_UNKNOWN) {
            sendKeyPress(keycode, EfficientAndroidLWJGLKeycode.getLwjglChar(keycode), CallbackBridge.getCurrentMods(), isDown);
        } else {
            mButton.sendSpecialKey(keycode, isDown);
        }
    }

    /** A look joystick works from the press: the other buttons fade at once, the turning waits for a drag. */
    void onDown(MotionEvent event) {
        mDownX = event.getX();
        mDownY = event.getY();
        mLookX = 0;
        mLookY = 0;
        if (props().passThruEnabled && props().lookJoystick && CallbackBridge.isGrabbing()) startDragging();
    }

    /**
     * A pass-through drag keeps the button pressed while the finger turns the camera, so it never swipes onto other
     * buttons. True: the look joystick took the move; false: the game surface gets it.
     */
    boolean onPassThruMove(MotionEvent event) {
        mLookX = event.getX() - mDownX;
        mLookY = event.getY() - mDownY;
        if (!mDragging && Math.hypot(mLookX, mLookY) > ViewConfiguration.get(mButton.getContext()).getScaledTouchSlop()) {
            startDragging();
        }
        return props().lookJoystick;
    }

    void onUp() {
        if (!mDragging) return;
        mDragging = false;
        mButton.removeCallbacks(mLookFrame);
        layout().extras().setDragging(null);
    }

    private void startDragging() {
        mDragging = true;
        layout().extras().setDragging(mButton);
        if (props().lookJoystick) {
            mLookFrameNanos = System.nanoTime();
            mButton.postOnAnimation(mLookFrame);
        }
    }
}
