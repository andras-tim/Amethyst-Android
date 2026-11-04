package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;

import org.lwjgl.glfw.CallbackBridge;

/**
 * The soft keyboard as a PC keyboard: a typed character is a key press and the character, so the key bindings of the
 * game and the mods (map, statistics, inventory) work in the game and on screens, and text fields get the text. The
 * keys go without modifiers: an upper case letter presses the same key as the lower case one, and types the upper case
 * character. A character without a key, such as an accented letter, is only typed.
 */
public class GameKeyInputConnection extends BaseInputConnection {
    // GLFW numbers these keys by their US ASCII character
    private static final String ASCII_KEYS = " '-,./;=[\\]`0123456789";

    // Some keyboards compose even without a text field: only the characters added since the last update are new
    private CharSequence mComposing = "";

    public GameKeyInputConnection(View view) {
        super(view, false);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        CharSequence composing = mComposing;
        mComposing = "";
        pressKeys(newSince(composing, text));
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        pressKeys(newSince(mComposing, text));
        mComposing = text.toString();
        return true;
    }

    @Override
    public boolean finishComposingText() {
        mComposing = "";
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        for (int i = 0; i < beforeLength; i++) pressKey(LwjglGlfwKeycode.GLFW_KEY_BACKSPACE);
        return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        int index = EfficientAndroidLWJGLKeycode.getIndexByKey(event.getKeyCode());
        if (!EfficientAndroidLWJGLKeycode.containsIndex(index)) return false;
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
        if (!isDown && event.getAction() != KeyEvent.ACTION_UP) return true;
        CallbackBridge.sendKeyPress(EfficientAndroidLWJGLKeycode.getValueByIndex(index), (char) event.getUnicodeChar(), 0, 0, isDown);
        return true;
    }

    private static CharSequence newSince(CharSequence previous, CharSequence text) {
        if (previous.length() > 0 && text.length() >= previous.length()
                && text.subSequence(0, previous.length()).toString().contentEquals(previous)) {
            return text.subSequence(previous.length(), text.length());
        }
        return text;
    }

    private static void pressKeys(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            int key = toGlfwKey(character);
            if (key != 0) CallbackBridge.sendKeyPress(key, '\u0000', 0, 0, true);
            if (character != '\n') CallbackBridge.sendChar(character, 0);
            if (key != 0) CallbackBridge.sendKeyPress(key, '\u0000', 0, 0, false);
        }
    }

    private static void pressKey(int key) {
        CallbackBridge.sendKeyPress(key, '\u0000', 0, 0, true);
        CallbackBridge.sendKeyPress(key, '\u0000', 0, 0, false);
    }

    static int toGlfwKey(char character) {
        char lower = Character.toLowerCase(character);
        if (lower >= 'a' && lower <= 'z') return LwjglGlfwKeycode.GLFW_KEY_A + (lower - 'a');
        if (ASCII_KEYS.indexOf(character) >= 0) return character;
        if (character == '\n') return LwjglGlfwKeycode.GLFW_KEY_ENTER;
        return 0;
    }
}
