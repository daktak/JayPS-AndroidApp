package com.njackson.ui.dashboard

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.njackson.Constants
import com.njackson.events.BleServiceCommand.BleSensorData
import com.njackson.events.GPSServiceCommand.GPSStatus
import com.njackson.events.GPSServiceCommand.NewAltitude
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.events.GPSServiceCommand.ResetGPSState
import com.njackson.events.GPSServiceCommand.SavedLocation
import com.njackson.events.base.BaseStatus
import com.njackson.state.IGPSDataStore
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(
    private val bus: Bus,
    private val store: IGPSDataStore,
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState(units = store.getMeasurementUnits()))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var hrm = false
    private var power = false
    private var cadence = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == Constants.PREF_PEBBLE_HRM) updateHrm()
        if (k == "UNITS_OF_MEASURE") _state.value = _state.value.copy(units = store.getMeasurementUnits())
    }

    init {
        bus.register(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        updateHrm()
        val u = store.getMeasurementUnits()
        _state.value = _state.value.copy(units = u)
    }

    override fun onCleared() {
        bus.unregister(this)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun updateHrm() {
        val pebbleHrm = prefs.getBoolean(Constants.PREF_PEBBLE_HRM, false)
        _state.value = _state.value.copy(hasHrm = pebbleHrm || hrm, hasPower = power, hasCadence = cadence)
    }

    @Subscribe fun onNewLocation(e: NewLocation) { applyLocation(e) }
    @Subscribe fun onSavedLocation(e: SavedLocation) { applyLocation(e) }

    private fun applyLocation(e: com.njackson.events.GPSServiceCommand.MyLocation) {
        val cur = _state.value
        val newTrail = if (e.getLatitude() != 0.0 || e.getLongitude() != 0.0) {
            val pt = TrailPoint(e.getLatitude(), e.getLongitude())
            val list = cur.trail
            if (list.isEmpty() || distanceBetween(list.last(), pt) > 2.0) {
                val appended = list + pt
                if (appended.size > 5000) appended.takeLast(5000) else appended
            } else list
        } else cur.trail
        _state.value = cur.copy(
            speed = e.getSpeed(),
            avgSpeed = e.getAverageSpeed(),
            distance = e.getDistance(),
            elapsedSec = e.getElapsedTimeSeconds(),
            ascent = e.getAscent(),
            maxSpeed = e.getMaxSpeed(),
            heartRate = e.getHeartRate(),
            power = e.getPower(),
            cadence = e.getCyclingCadence(),
            accuracy = e.getAccuracy(),
            units = e.getUnits(),
            trail = newTrail,
        )
        if (e.getHeartRate() in 1..254) hrm = true
        if (e.getPower() >= 0) power = true
        if (e.getCyclingCadence() in 1..254) cadence = true
        updateHrm()
    }

    private fun distanceBetween(a: TrailPoint, b: TrailPoint): Double {
        val dLat = a.lat - b.lat
        val dLon = a.lon - b.lon
        return Math.sqrt(dLat * dLat + dLon * dLon) * 111000.0
    }

    @Subscribe fun onNewAltitude(e: NewAltitude) {
        _state.value = _state.value.copy(altitudes = e.getAltitudes().toList())
    }

    @Subscribe fun onResetGPSState(@Suppress("UNUSED_PARAMETER") e: ResetGPSState) {
        hrm = false; power = false; cadence = false
        _state.value = DashboardUiState(units = store.getMeasurementUnits())
    }

    @Subscribe fun onGPSStatus(e: GPSStatus) {
        val running = e.getStatus() == BaseStatus.Status.STARTED
        _state.value = _state.value.copy(isRunning = running)
        if (e.getStatus() == BaseStatus.Status.STOPPED) {
            val raw = store.getDistance()
            val u = store.getMeasurementUnits()
            val converted = when (u) {
                Constants.IMPERIAL, Constants.RUNNING_IMPERIAL -> raw * Constants.M_TO_MILES
                Constants.METRIC, Constants.RUNNING_METRIC -> raw * Constants.M_TO_KM
                Constants.NAUTICAL_IMPERIAL, Constants.NAUTICAL_METRIC -> raw * Constants.M_TO_NM
                else -> raw * Constants.M_TO_KM
            }
            _state.value = _state.value.copy(elapsedSec = (store.getElapsedTime() / 1000).toInt(), distance = converted, units = u)
        }
    }

    @Subscribe fun onBle(e: BleSensorData) {
        when (e.getType()) {
            BleSensorData.SENSOR_HRM -> hrm = true
            BleSensorData.SENSOR_POWER -> power = true
            BleSensorData.SENSOR_CSC_CADENCE, BleSensorData.SENSOR_RSC -> cadence = true
        }
        updateHrm()
    }
}
