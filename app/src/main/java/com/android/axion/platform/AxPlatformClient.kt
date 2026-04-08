package com.android.axion.platform

import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

class AxPlatformClient {
    open class Listener {
        open fun onFeatureChanged(feature: String, active: Boolean) {}
        open fun onStateChanged(key: String, state: Bundle) {}
    }

    companion object {
        const val TAG = "AxPlatformClientShim"
        
        const val FEATURE_WIFI = "wifi"
        const val FEATURE_BLUETOOTH = "bluetooth"
        const val FEATURE_ZEN = "zen"
        const val FEATURE_ROTATION = "rotation"
        const val FEATURE_MOBILE_DATA = "mobile_data"
        const val FEATURE_AIRPLANE_MODE = "airplane_mode"
        const val FEATURE_FLASHLIGHT = "flashlight"
        const val FEATURE_DARK_MODE = "dark_mode"
        const val FEATURE_LOCATION = "location"
        const val FEATURE_BATTERY_SAVER = "battery_saver"
        const val FEATURE_HOTSPOT = "hotspot"
        const val FEATURE_NFC = "nfc"
        const val FEATURE_NIGHT_LIGHT = "night_light"
        const val FEATURE_AOD = "aod"
        const val FEATURE_DATA_SAVER = "data_saver"
        const val FEATURE_COLOR_INVERSION = "color_inversion"
        const val FEATURE_COLOR_CORRECTION = "color_correction"
        const val FEATURE_REDUCE_BRIGHTNESS = "reduce_brightness"
        const val FEATURE_ONE_HANDED_MODE = "one_handed_mode"
        const val FEATURE_HEADS_UP = "heads_up"
        const val FEATURE_AUTO_SYNC = "auto_sync"
        const val FEATURE_CAMERA_PRIVACY = "camera_privacy"
        const val FEATURE_MIC_PRIVACY = "mic_privacy"
        const val FEATURE_WORK_PROFILE = "work_profile"
        const val FEATURE_USB_TETHER = "usb_tether"
        const val FEATURE_DREAM = "dream"
        const val FEATURE_READING_MODE = "reading_mode"
        const val FEATURE_POWER_SHARE = "power_share"
        const val FEATURE_CAFFEINE = "caffeine"
        const val FEATURE_VPN = "vpn"
        const val FEATURE_CAST = "cast"
        const val FEATURE_PROFILES = "profiles"
        const val FEATURE_SMART_PIXELS = "smart_pixels"
        const val FEATURE_SCREEN_RECORD = "screen_record"
        const val FEATURE_SCREENSHOT = "screenshot"

        private var instance: AxPlatformClient? = null

        fun getInstance(): AxPlatformClient {
            if (instance == null) {
                instance = AxPlatformClient()
            }
            return instance!!
        }

        fun getLabel(state: Bundle): String? {
            return state.getString("label")
        }
    }

    private val listeners = mutableListOf<Listener>()
    private var mContext: Context? = null
    
    private var mCameraManager: CameraManager? = null
    private var mTorchState = false

    fun init(context: Context) {
        mContext = context
        initFlashlightTracker()
    }
    
    private fun notifyListeners(feature: String, active: Boolean) {
        val state = Bundle().apply { putBoolean("active", active) }
        listeners.forEach {
            it.onFeatureChanged(feature, active)
            it.onStateChanged(feature, state)
        }
    }

    private fun initFlashlightTracker() {
        mCameraManager = mContext?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        mCameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                mTorchState = enabled
                notifyListeners(FEATURE_FLASHLIGHT, enabled)
            }
        }, null)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun toggle(feature: String) {
        val ctx = mContext ?: return
        val contentResolver = ctx.contentResolver
        
        try {
            when (feature) {
                FEATURE_WIFI -> {
                    val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val isEnabled = wifiManager.isWifiEnabled
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = !isEnabled
                    notifyListeners(feature, !isEnabled)
                }
                FEATURE_BLUETOOTH -> {
                    val btAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (btAdapter != null) {
                        if (btAdapter.isEnabled) {
                            @Suppress("DEPRECATION")
                            btAdapter.disable()
                            notifyListeners(feature, false)
                        } else {
                            @Suppress("DEPRECATION")
                            btAdapter.enable()
                            notifyListeners(feature, true)
                        }
                    }
                }
                FEATURE_ROTATION -> {
                    val isEnabled = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
                    Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (isEnabled) 0 else 1)
                    notifyListeners(feature, !isEnabled)
                }
                FEATURE_DARK_MODE -> {
                    val uiModeManager = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    val isNightMode = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
                    uiModeManager.nightMode = if (isNightMode) UiModeManager.MODE_NIGHT_NO else UiModeManager.MODE_NIGHT_YES
                    notifyListeners(feature, !isNightMode)
                }
                FEATURE_FLASHLIGHT -> {
                    mCameraManager?.let { cm ->
                        cm.cameraIdList.firstOrNull()?.let { id ->
                            cm.setTorchMode(id, !mTorchState)
                        }
                    }
                }
                FEATURE_HEADS_UP -> {
                    val isEnabled = Settings.Global.getInt(contentResolver, "heads_up_notifications_enabled", 1) == 1
                    Settings.Global.putInt(contentResolver, "heads_up_notifications_enabled", if (isEnabled) 0 else 1)
                    notifyListeners(feature, !isEnabled)
                }
                FEATURE_CAFFEINE -> {
                    val isEnabled = Settings.System.getInt(contentResolver, "caffeine_enabled", 0) == 1
                    Settings.System.putInt(contentResolver, "caffeine_enabled", if (isEnabled) 0 else 1)
                    notifyListeners(feature, !isEnabled)
                }
                FEATURE_SMART_PIXELS -> {
                    val isEnabled = Settings.System.getInt(contentResolver, "smart_pixels_enable", 0) == 1
                    Settings.System.putInt(contentResolver, "smart_pixels_enable", if (isEnabled) 0 else 1)
                    notifyListeners(feature, !isEnabled)
                }
                FEATURE_SCREEN_RECORD -> {
                    // Triggering standard SystemUI Screen Record
                    val intent = Intent("com.android.systemui.screenrecord.START")
                    intent.setPackage("com.android.systemui")
                    ctx.sendBroadcast(intent)
                }
                FEATURE_SCREENSHOT -> {
                    // Triggering standard screen capture intent
                    val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    ctx.sendBroadcast(intent)
                    // The actual screenshot is usually invoked by SystemUI, handled via Intent in GameBarService
                }
                FEATURE_ZEN -> {
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val isDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    if (isDnd) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        notifyListeners(feature, false)
                    } else {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        notifyListeners(feature, true)
                    }
                }
                // Fallbacks to generic toggles
                else -> {
                    Log.d(TAG, "Toggling unmapped feature: \$feature")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle feature \$feature: \${e.message}")
        }
    }

    fun getState(feature: String): Bundle {
        val bundle = Bundle()
        val ctx = mContext ?: return bundle
        val contentResolver = ctx.contentResolver
        
        var active = false
        try {
            active = when (feature) {
                FEATURE_WIFI -> {
                    (ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
                }
                FEATURE_BLUETOOTH -> {
                    BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
                }
                FEATURE_ROTATION -> {
                    Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
                }
                FEATURE_DARK_MODE -> {
                    (ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.nightMode == UiModeManager.MODE_NIGHT_YES
                }
                FEATURE_FLASHLIGHT -> {
                    mTorchState
                }
                FEATURE_HEADS_UP -> {
                    Settings.Global.getInt(contentResolver, "heads_up_notifications_enabled", 1) == 1
                }
                FEATURE_CAFFEINE -> {
                    Settings.System.getInt(contentResolver, "caffeine_enabled", 0) == 1
                }
                FEATURE_SMART_PIXELS -> {
                    Settings.System.getInt(contentResolver, "smart_pixels_enable", 0) == 1
                }
                FEATURE_ZEN -> {
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get state for \$feature: \${e.message}")
        }
        
        bundle.putBoolean("active", active)
        return bundle
    }
}
