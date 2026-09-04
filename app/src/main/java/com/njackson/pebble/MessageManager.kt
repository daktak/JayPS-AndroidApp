package com.njackson.pebble

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.njackson.Constants
import com.njackson.adapters.AdvancedLocationToNewLocation
import com.njackson.adapters.buildLocationDictionary
import com.njackson.adapters.putUInt16
import com.njackson.adapters.putUInt8
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.application.modules.ForApplication
import com.njackson.gps.Navigator
import com.njackson.utils.BatteryStatus
import fr.jayps.android.AdvancedLocation
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class MessageManager @Inject constructor(
    private val prefs: SharedPreferences,
    private val navigator: Navigator,
    @ForApplication private val context: Context
) : IMessageManager {

    private val tag = "PB-MessageManager"
    private val debug = prefs.getBoolean("PREF_DEBUG", false)
    private val uuid = Constants.WATCH_UUID
    private val sender = DefaultPebbleSender(context)
    private val handler = CoroutineExceptionHandler { _, e ->
        Log.e(tag, "pebble communication failed", e)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    private val queue = LinkedBlockingQueue<PebbleDictionary>()
    private val connected = java.util.concurrent.atomic.AtomicBoolean(false)
    private var skipped = 0

    private fun pump() {
        scope.launch {
            while (queue.isNotEmpty()) {
                val data = queue.peek() ?: break
                try {
                    sender.sendDataToPebble(uuid, data)
                    connected.set(true)
                } catch (e: Exception) {
                    Log.e(tag, "send failed", e)
                }
                queue.poll()
            }
        }
    }

    override fun offer(data: PebbleDictionary): Boolean {
        val ok = queue.offer(data)
        if (ok) pump()
        return ok
    }

    override fun offerIfLow(data: PebbleDictionary, sizeMax: Int): Boolean {
        synchronized(queue) {
            if (queue.size > sizeMax) {
                if (connected.get()) {
                    skipped++
                    if (skipped % 20 == 19) {
                        queue.poll()
                        pump()
                    }
                }
                return false
            }
            skipped = 0
        }
        val ok = queue.offer(data)
        if (ok) pump()
        return ok
    }

    override fun showWatchFace() {
        scope.launch { sender.startAppOnTheWatch(uuid) }
    }

    override fun hideWatchFace() {
        scope.launch { sender.stopAppOnTheWatch(uuid) }
    }

    override fun showSimpleNotificationOnWatch(title: String, text: String) {
        // The classic PebbleKit SEND_NOTIFICATION broadcast targeted the dead official Pebble app.
        // With pebblekit2, rich notifications go through the selected companion app (CoreApp/microPebble).
        // Kept as a no-op so callers keep working; wire to a timeline pin if needed later.
        if (debug) Log.i(tag, "showSimpleNotificationOnWatch($title): $text")
    }

    override fun sendMessageToPebble(title: String, message: String) {
        showSimpleNotificationOnWatch(title, message)
    }

    override fun sendSavedDataToPebble(
        isLocationServicesRunning: Boolean,
        units: Int,
        distance: Float,
        elapsedTime: Long,
        ascent: Float,
        maxSpeed: Float
    ) {
        val advancedLocation = AdvancedLocation().apply {
            setDistance(distance)
            setElapsedTime(elapsedTime)
            setAscent(ascent.toDouble())
            setMaxSpeed(maxSpeed)
        }
        val newLocation = AdvancedLocationToNewLocation(advancedLocation, 0.0, 0.0, units).apply {
            setBatteryLevel(BatteryStatus.getBatteryLevel(context))
            setSendNavigation(navigator.nbPoints > 0)
        }

        val dict = buildLocationDictionary(
            newLocation,
            navigator,
            isLocationServicesRunning,
            prefs.getBoolean("PREF_DEBUG", false),
            prefs.getBoolean("LIVE_TRACKING", false),
            prefs.getString("REFRESH_INTERVAL", Constants.REFRESH_INTERVAL_DEFAULT.toString())!!.toInt(),
            prefs.getInt("WATCHFACE_VERSION", 0),
            prefs.getBoolean("NAV_NOTIFICATION", false)
        ).toMutableMap()

        val versionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
        dict[Constants.MSG_VERSION_ANDROID.toUInt()] = PebbleDictionaryItem.Int32(versionCode)

        val hrMax = ByteArray(2)
        putUInt8(hrMax, 0, prefs.getString("PREF_BLE_HRM_HRMAX", "0")!!.toInt() % 256)
        putUInt8(hrMax, 1, prefs.getString("PREF_BLE_HRM_ZONE_NOTIFICATION_MODE", "0")!!.toInt() % 256)
        dict[Constants.MSG_HR_MAX.toUInt()] = PebbleDictionaryItem.Bytes(hrMax)

        val ftp = ByteArray(2)
        putUInt16(ftp, 0, prefs.getString(Constants.PREF_FTP, "0")!!.toIntOrNull() ?: 0)
        dict[Constants.MSG_FTP.toUInt()] = PebbleDictionaryItem.Bytes(ftp)

        offer(dict)
    }
}
