package com.njackson.pebble

import android.util.Log
import com.njackson.Constants
import com.njackson.application.PebbleBikeApplication
import com.njackson.events.BleServiceCommand.BleSensorData
import com.njackson.events.GPSServiceCommand.ResetGPSState
import com.njackson.events.PebbleServiceCommand.NewMessage
import com.njackson.oruxmaps.IOruxMaps
import com.njackson.state.IGPSDataStore
import com.njackson.utils.services.IServiceStarter
import com.squareup.otto.Bus
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import fr.jayps.android.AdvancedLocation
import java.util.UUID
import javax.inject.Inject

class PebbleListenerService : BasePebbleListenerService() {

    private val tag = "PB-PebbleListenerService"

    @Inject lateinit var oruxMaps: IOruxMaps
    @Inject lateinit var messageManager: IMessageManager
    @Inject lateinit var bus: Bus
    @Inject lateinit var serviceStarter: IServiceStarter
    @Inject lateinit var dataStore: IGPSDataStore
    @Inject lateinit var prefs: android.content.SharedPreferences

    override fun onCreate() {
        super.onCreate()
        (applicationContext as PebbleBikeApplication).inject(this)
    }

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: io.rebble.pebblekit2.common.model.WatchIdentifier
    ): ReceiveResult {
        if (watchappUUID != Constants.WATCH_UUID) return ReceiveResult.Nack

        data[Constants.CMD_BUTTON_PRESS.toUInt()]?.let { handleButtonData(intOf(it)) }
        data[Constants.MSG_VERSION_PEBBLE.toUInt()]?.let { handleVersion(intOf(it)) }
        data[Constants.MSG_CONFIG.toUInt()]?.let { handleConfig(it) }
        data[Constants.PEBBLE_MSG_HEART_RATE.toUInt()]?.let { handlePebbleHeartRate(intOf(it)) }
        return ReceiveResult.Ack
    }

    private fun intOf(item: PebbleDictionaryItem): Int = when (item) {
        is PebbleDictionaryItem.Int32 -> item.value
        is PebbleDictionaryItem.UInt32 -> item.value.toInt()
        is PebbleDictionaryItem.Int16 -> item.value.toInt()
        is PebbleDictionaryItem.UInt16 -> item.value.toInt()
        is PebbleDictionaryItem.Int8 -> item.value.toInt()
        is PebbleDictionaryItem.UInt8 -> item.value.toInt()
        else -> 0
    }

    private fun handleVersion(version: Int) {
        Log.i(tag, "handleVersion:$version min:${Constants.MIN_VERSION_PEBBLE} last:${Constants.LAST_VERSION_PEBBLE}")
        if (version < Constants.LAST_VERSION_PEBBLE) {
            // newer watchface available
        }
        prefs.edit().putInt("WATCHFACE_VERSION", version).apply()
        sendSavedData()
    }

    private fun handleConfig(item: PebbleDictionaryItem) {
        if (item !is PebbleDictionaryItem.Bytes) return
        val config = item.value
        val sb = StringBuilder()
        for (b in config) sb.append(String.format("%02X", b))
        prefs.edit().putString("WATCHFACE_CONFIG", sb.toString()).apply()
    }

    private fun handlePebbleHeartRate(heartRate: Int) {
        if (heartRate <= 0 || heartRate > 255) return
        // Feed the watch's built-in HR sensor into the app exactly like a Bluetooth HRM.
        val sensorData = BleSensorData("pebble")
        sensorData.setHeartRate(heartRate)
        bus.post(sensorData)
    }

    private fun handleButtonData(button: Int) {
        Log.i(tag, "handleButtonData:$button")
        when (button) {
            Constants.ORUXMAPS_START_RECORD_CONTINUE_PRESS -> oruxMaps.startRecordNewSegment()
            Constants.ORUXMAPS_STOP_RECORD_PRESS -> oruxMaps.stopRecord()
            Constants.ORUXMAPS_NEW_WAYPOINT_PRESS -> oruxMaps.newWaypoint()
            Constants.STOP_PRESS -> serviceStarter.stopLocationServices()
            Constants.PLAY_PRESS -> serviceStarter.startLocationServices()
            Constants.REFRESH_PRESS -> {
                resetSavedData()
                bus.post(ResetGPSState())
            }
        }
    }

    private fun resetSavedData() {
        dataStore.resetAllValues()
        dataStore.commit()
        AdvancedLocation(applicationContext).resetGPX()
        if (!serviceStarter.isLocationServicesRunning) {
            sendSavedData()
        }
    }

    private fun sendSavedData() {
        val isLocationServicesRunning = serviceStarter.isLocationServicesRunning
        messageManager.sendSavedDataToPebble(
            isLocationServicesRunning,
            dataStore.measurementUnits,
            dataStore.distance,
            dataStore.elapsedTime,
            dataStore.ascent,
            dataStore.maxSpeed
        )
    }

    private fun sendMessageToPebble(message: String) {
        messageManager.sendMessageToPebble("KayPS", message)
    }
}
