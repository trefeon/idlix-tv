package com.idlix.tv.ui.input

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Wireless (Bluetooth/USB) game-controller key mapping for the TV app.
 *
 * Notes on platform behavior (verified by inspection, no custom code needed):
 * - Most gamepad buttons arrive as [KeyEvent] key codes; the D-pad, A/B/X/Y,
 *   bumpers, triggers, START/SELECT and media keys are all key events.
 * - Analog sticks do NOT need custom axis polling: the OS key layout maps
 *   stick deflection to KEYCODE_DPAD_* key events, which the existing
 *   DPAD handling (focus + player seeks) already consumes. No MotionEvent /
 *   AXIS_* code exists or is required in this repo.
 * - Remote-only users are unaffected: every existing key path is untouched;
 *   this file only ADDS handling for key codes remotes never emit
 *   (KEYCODE_BUTTON_*), plus KEYCODE_BUTTON_B mirroring KEYCODE_BACK.
 */
object GamepadKeys {

    /** Keys that confirm / select the focused control (focus system handles them). */
    fun isConfirmKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> true
        else -> false
    }

    /** Keys that mean "back" wherever system Back works. */
    fun isBackKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_ESCAPE -> true
        else -> false
    }

    /** True for gamepad-only button codes a TV remote never emits. */
    fun isGamepadButton(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_Z,
        KeyEvent.KEYCODE_BUTTON_1,
        KeyEvent.KEYCODE_BUTTON_2,
        KeyEvent.KEYCODE_BUTTON_3,
        KeyEvent.KEYCODE_BUTTON_4,
        KeyEvent.KEYCODE_BUTTON_5,
        KeyEvent.KEYCODE_BUTTON_6,
        KeyEvent.KEYCODE_BUTTON_7,
        KeyEvent.KEYCODE_BUTTON_8,
        KeyEvent.KEYCODE_BUTTON_9,
        KeyEvent.KEYCODE_BUTTON_10,
        KeyEvent.KEYCODE_BUTTON_11,
        KeyEvent.KEYCODE_BUTTON_12,
        KeyEvent.KEYCODE_BUTTON_13,
        KeyEvent.KEYCODE_BUTTON_14,
        KeyEvent.KEYCODE_BUTTON_15,
        KeyEvent.KEYCODE_BUTTON_16 -> true
        else -> false
    }

    /**
     * Player callbacks for gamepad-only buttons. Each returns true when it
     * consumed the key. Implemented by PlayerScreen from its existing state
     * (same primitives the DPAD/media branches already use).
     */
    class PlayerActions(
        val onConfirm: () -> Boolean,
        val onTogglePlayPause: () -> Boolean,
        val onSeekForward: () -> Boolean,
        val onSeekBackward: () -> Boolean,
        val onOpenSubtitlePicker: () -> Boolean,
        val onToggleControls: () -> Boolean
    )

    /**
     * Maps gamepad-only key codes to player actions. Returns true when
     * consumed. BACK-equivalent (BUTTON_B) is intentionally NOT handled here:
     * the caller normalizes BUTTON_B to KEYCODE_BACK so it flows through the
     * exact same branch the remote's Back key uses today.
     *
     * @param inPlayer false outside the player: nothing consumed here (the
     *   nav-root back handler deals with BUTTON_B; focus handles the rest).
     */
    fun onGamepadKey(keyCode: Int, inPlayer: Boolean, actions: PlayerActions): Boolean {
        if (!inPlayer) return false
        return when (keyCode) {
            // A = confirm, same as DPAD_CENTER (show controls when hidden).
            KeyEvent.KEYCODE_BUTTON_A -> actions.onConfirm()
            // X or START = play/pause toggle.
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_START -> actions.onTogglePlayPause()
            // Y = subtitle picker.
            KeyEvent.KEYCODE_BUTTON_Y -> actions.onOpenSubtitlePicker()
            // R1/R2 = +10s, L1/L2 = -10s (matches DPAD_LEFT/RIGHT distance).
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_R2 -> actions.onSeekForward()
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_L2 -> actions.onSeekBackward()
            // SELECT = toggle controls visibility.
            KeyEvent.KEYCODE_BUTTON_SELECT -> actions.onToggleControls()
            else -> false
        }
    }
}

/**
 * InputDevice helpers for the connect/disconnect indicator. Kept separate from
 * [GamepadKeys] so the pure key mapping stays unit-testable on the JVM
 * (android.jar stubs throw on method calls; key-code constants are inlined).
 */
object GamepadDevices {

    /**
     * Name of the first connected input device that reports BUTTON_A,
     * or null when no gamepad is present. Per spec: InputDevice.getDeviceIds
     * -> hasKeys(BUTTON_A)[0].
     */
    fun findConnectedGamepadName(): String? {
        return try {
            for (id in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(id) ?: continue
                val hasA = try {
                    device.hasKeys(KeyEvent.KEYCODE_BUTTON_A)[0]
                } catch (_: Exception) {
                    false
                }
                if (hasA) return device.name
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
