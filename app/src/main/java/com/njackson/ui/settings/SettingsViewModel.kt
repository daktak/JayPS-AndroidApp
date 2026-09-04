package com.njackson.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import com.njackson.Constants
import com.njackson.events.GPSServiceCommand.ChangeIndoorMode
import com.njackson.events.GPSServiceCommand.ChangeRefreshInterval
import com.njackson.events.PebbleServiceCommand.HrMonitorEnable
import com.njackson.state.IGPSDataStore
import com.squareup.otto.Bus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val prefs: SharedPreferences,
    private val store: IGPSDataStore,
    private val bus: Bus,
    private val ctx: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = load()
        store.reloadPreferencesFromSettings()
    }

    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    override fun onCleared() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }

    private fun load(): SettingsUiState {
        val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressure = mgr.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        return SettingsUiState(
            units = prefs.getString("UNITS_OF_MEASURE", "1") ?: "1",
            refreshInterval = prefs.getString("REFRESH_INTERVAL", "103000") ?: "103000",
            indoorMode = prefs.getBoolean(Constants.PREF_INDOOR_MODE, false),
            enableTracks = prefs.getBoolean("ENABLE_TRACKS", true),
            advancedGpx = prefs.getBoolean("ADVANCED_GPX", false),
            exportEmail = prefs.getString("EXPORT_EMAIL", "") ?: "",
            tcxActivity = prefs.getString("TCX_ACTIVITY_TYPE", "Biking") ?: "Biking",
            activityRecognition = prefs.getBoolean("ACTIVITY_RECOGNITION", false),
            activityWalking = prefs.getBoolean("ACTIVITY_RECOGNITION_WALKING", false),
            bleNames = (1..6).map { prefs.getString("hrm_name$it", "") ?: "" },
            bleAddresses = (1..6).map { prefs.getString("hrm_address$it", "") ?: "" },
            hrmMax = prefs.getString("PREF_BLE_HRM_HRMAX", "0") ?: "0",
            hrmZone = prefs.getString("PREF_BLE_HRM_ZONE_NOTIFICATION_MODE", "0") ?: "0",
            pebbleHrm = prefs.getBoolean(Constants.PREF_PEBBLE_HRM, false),
            ftp = prefs.getString(Constants.PREF_FTP, "0") ?: "0",
            wheelPreset = prefs.getString("PREF_BLE_CSC_WHEEL_PRESET", "") ?: "",
            wheelSize = prefs.getString("PREF_BLE_CSC_WHEEL_SIZE", "") ?: "",
            liveTracking = prefs.getBoolean("LIVE_TRACKING", false),
            liveUrl = prefs.getString("LIVE_TRACKING_URL", "") ?: "",
            liveToken = prefs.getString("LIVE_TRACKING_TOKEN", "") ?: "",
            liveDevice = prefs.getString("LIVE_TRACKING_DEVICE", "") ?: "",
            liveMmt = prefs.getBoolean("LIVE_TRACKING_MMT", false),
            liveMmtLogin = prefs.getString("LIVE_TRACKING_MMT_LOGIN", "") ?: "",
            liveMmtPassword = prefs.getString("LIVE_TRACKING_MMT_PASSWORD", "") ?: "",
            oruxAuto = prefs.getString("ORUXMAPS_AUTO", "disable") ?: "disable",
            stravaSession = prefs.getString("STRAVA_SESSION", "") ?: "",
            stravaAuto = prefs.getString("STRAVA_AUTO", "disable") ?: "disable",
            debug = prefs.getBoolean("PREF_DEBUG", false),
            pressureAvailable = pressure,
            geoidHeight = prefs.getFloat("GEOID_HEIGHT", 0f),
            autostartLights = prefs.getBoolean(Constants.PREF_AUTOSTART_LIGHTS, true),
            autostartGoPro = prefs.getBoolean(Constants.PREF_AUTOSTART_GOPRO, true),
        )
    }

    fun putString(k: String, v: String) { prefs.edit().putString(k, v).apply(); handleSide(k, v) }
    fun putBool(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply(); handleSide(k, v.toString()) }
    private fun handleSide(k: String, v: String) {
        store.reloadPreferencesFromSettings()
        when (k) {
            "REFRESH_INTERVAL" -> try { bus.post(ChangeRefreshInterval(v.toInt())) } catch (_: Exception) {}
            Constants.PREF_INDOOR_MODE -> { handleIndoor(v == "true"); bus.post(ChangeIndoorMode(v == "true")) }
            Constants.PREF_PEBBLE_HRM -> bus.post(HrMonitorEnable(if (v == "true") 1 else 0))
            "PREF_BLE_CSC_WHEEL_PRESET" -> if (v.isNotEmpty()) putString("PREF_BLE_CSC_WHEEL_SIZE", v)
        }
        _state.value = load()
    }
    private fun handleIndoor(indoor: Boolean) {
        val cur = prefs.getString("REFRESH_INTERVAL", "103000") ?: "103000"
        val adaptive = cur == "103000" || cur == "203000" || cur == "305000"
        if (indoor && adaptive) {
            prefs.edit().putString("INDOOR_MODE_PREV_REFRESH", cur).putString("REFRESH_INTERVAL", "1000").apply()
            try { bus.post(ChangeRefreshInterval(1000)) } catch (_: Exception) {}
        } else if (!indoor) {
            val prev = prefs.getString("INDOOR_MODE_PREV_REFRESH", "") ?: ""
            if (prev.isNotEmpty()) {
                prefs.edit().putString("REFRESH_INTERVAL", prev).remove("INDOOR_MODE_PREV_REFRESH").apply()
                try { bus.post(ChangeRefreshInterval(prev.toInt())) } catch (_: Exception) {}
            }
        }
    }
    fun clearBle(i: Int) { prefs.edit().putString("hrm_name${i+1}", "").putString("hrm_address${i+1}", "").apply(); _state.value = load() }
    fun setBle(i: Int, name: String, addr: String) { prefs.edit().putString("hrm_name${i+1}", name).putString("hrm_address${i+1}", addr).apply(); _state.value = load() }
}
