package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

import fr.spse.gamepad_remapper.Remapper;

/**
 * Stores the map of a standard Android gamepad from its reported axes and keys, so the remapper wizard only opens for a
 * device that cannot be described this way. The stored map is the same as the wizard's result, keyed by device.
 */
public final class GamepadAutoMap {
    private static final int[] BUTTONS = {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT
    };
    private static final String PREFERENCES = "gamepad_auto_map";
    private static final String WIZARD_REQUESTED = "wizard_requested";

    private GamepadAutoMap() {}

    /**
     * @return true if a new map was stored, so the RemapperManager has to be rebuilt to see it
     */
    public static boolean store(Context context, InputDevice device) {
        if (device == null) return false;
        String descriptor = device.getDescriptor();
        SharedPreferences maps = context.getSharedPreferences(Remapper.SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        if (maps.contains(descriptor)) {
            preferences.edit().remove(WIZARD_REQUESTED).apply();
            return false;
        }
        if (preferences.getBoolean(WIZARD_REQUESTED, false)) return false;

        Map<Integer, Integer> keyMap = buildKeyMap(device);
        Map<Integer, Integer> motionMap = buildMotionMap(device);
        if (keyMap == null || motionMap == null) return false;
        new Remapper(keyMap, motionMap).save(context, descriptor);
        return true;
    }

    /** After a reset the next gamepad gets the wizard, otherwise a wrong map could never be replaced. */
    public static void requestWizard(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(WIZARD_REQUESTED, true).apply();
    }

    private static Map<Integer, Integer> buildKeyMap(InputDevice device) {
        Map<Integer, Integer> keyMap = new HashMap<>();
        boolean[] present = device.hasKeys(BUTTONS);
        for (int i = 0; i < BUTTONS.length; i++) {
            if (!present[i]) return null;
            keyMap.put(BUTTONS[i], BUTTONS[i]);
        }
        return keyMap;
    }

    private static Map<Integer, Integer> buildMotionMap(InputDevice device) {
        Map<Integer, Integer> motionMap = new HashMap<>();
        if (!mapPair(device, motionMap, MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
                || !mapPair(device, motionMap, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y)) {
            return null;
        }
        boolean rightStick = mapPair(device, motionMap, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
                || mapPair(device, motionMap, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);
        // BRAKE and GAS first: an Xbox controller over Bluetooth moves these, even if a device reports both pairs
        boolean triggers = mapPair(device, motionMap, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER)
                || mapPair(device, motionMap, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER);
        return rightStick && triggers ? motionMap : null;
    }

    private static boolean mapPair(InputDevice device, Map<Integer, Integer> motionMap, int first, int second, int firstTarget, int secondTarget) {
        if (device.getMotionRange(first) == null || device.getMotionRange(second) == null) return false;
        motionMap.put(first, firstTarget);
        motionMap.put(second, secondTarget);
        return true;
    }
}
