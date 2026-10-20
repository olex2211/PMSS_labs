package com.example.lab1.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper

data class TorchState(
    val simulated: Boolean,
    val enabled: Boolean = false,
    val available: Boolean = true,
    val error: String? = null,
)

/** Uses a simulated lamp on emulators and devices without a flash. */
class FlashlightController(
    private val context: Context,
    private val onState: (TorchState) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val isEmulator = Build.HARDWARE in setOf("goldfish", "ranchu", "cutf") ||
        Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("Emulator", ignoreCase = true)
    private val hasFlash = !isEmulator && context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    var state = TorchState(simulated = !hasFlash)
        private set
    private var cameraId: String? = null
    private var callbackRegistered = false
    private var requestedEnabled = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) {
                publish(state.copy(enabled = enabled, available = true, error = null))
                // A delayed callback must not leave our light on after onPause.
                if (enabled && !requestedEnabled) setEnabled(false)
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) {
                requestedEnabled = false
                publish(state.copy(enabled = false, available = false, error = "The flash is in use by another app."))
            }
        }
    }

    init {
        if (hasFlash) {
            try {
                cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId == null) {
                    state = state.copy(available = false, error = "No camera with a flash was found.")
                } else {
                    manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
                    callbackRegistered = true
                }
            } catch (error: CameraAccessException) {
                state = state.copy(available = false, error = "Unable to access the camera.")
            } catch (error: SecurityException) {
                state = state.copy(available = false, error = "Camera access is unavailable.")
            }
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (!hasFlash) {
            requestedEnabled = enabled
            publish(state.copy(enabled = enabled, error = null))
            return true
        }
        val id = cameraId ?: return false
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            publish(state.copy(error = "Grant camera access to turn on the flash."))
            return false
        }
        return try {
            requestedEnabled = enabled
            manager.setTorchMode(id, enabled)
            // The actual on/off state is confirmed by TorchCallback, not guessed here.
            true
        } catch (error: CameraAccessException) {
            requestedEnabled = false
            publish(state.copy(error = "The flash is unavailable. Close the camera and try again."))
            false
        } catch (error: SecurityException) {
            requestedEnabled = false
            publish(state.copy(error = "Grant camera access in the app settings."))
            false
        } catch (error: IllegalArgumentException) {
            requestedEnabled = false
            publish(state.copy(error = "This device does not support flash control."))
            false
        }
    }

    fun stop() {
        val wasRequested = requestedEnabled
        requestedEnabled = false
        if (state.enabled || wasRequested) setEnabled(false)
    }

    fun close() {
        stop()
        if (callbackRegistered) manager.unregisterTorchCallback(callback)
    }

    private fun publish(newState: TorchState) {
        state = newState
        onState(newState)
    }
}
