package com.idlix.tv

import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.idlix.tv.startup.ErrorLog
import com.idlix.tv.ui.input.GamepadDevices
import com.idlix.tv.ui.navigation.AppNavigation
import com.idlix.tv.ui.theme.IdlixTvTheme

class MainActivity : ComponentActivity() {

    private var inputManager: InputManager? = null

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            reportGamepadChange()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            reportGamepadChange()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            reportGamepadChange()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on while TV app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        inputManager = getSystemService(Context.INPUT_SERVICE) as? InputManager
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)
        // Silent inventory at launch: log only, no toast (avoids noise on start).
        GamepadDevices.findConnectedGamepadName()?.let { name ->
            ErrorLog.appendMessage(this, "gamepad", "Gamepad present at launch: $name")
        }

        setContent {
            IdlixTvTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        try {
            inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        } catch (_: Exception) {
        } finally {
            inputManager = null
        }
        super.onDestroy()
    }

    /**
     * Gamepad connect/disconnect indicator: toast the gamepad name when one is
     * present, always log to ErrorLog. Remote-only users never trigger this
     * (remotes don't report BUTTON_A).
     */
    private fun reportGamepadChange() {
        val name = GamepadDevices.findConnectedGamepadName()
        if (name != null) {
            Toast.makeText(this, "Gamepad connected: $name", Toast.LENGTH_SHORT).show()
            ErrorLog.appendMessage(this, "gamepad", "Gamepad connected: $name")
        } else {
            ErrorLog.appendMessage(this, "gamepad", "Input devices changed; no gamepad present")
        }
    }
}
