package com.njackson.pebble

import com.njackson.Constants
import com.njackson.adapters.buildLocationDictionary
import com.njackson.application.IInjectionContainer
import com.njackson.events.GPSServiceCommand.GPSStatus
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.events.PebbleServiceCommand.HrMonitorEnable
import com.njackson.events.PebbleServiceCommand.NewMessage
import com.njackson.events.base.BaseStatus
import com.njackson.gps.Navigator
import com.njackson.application.modules.ForApplication
import com.njackson.service.IServiceCommand
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import javax.inject.Inject

class PebbleServiceCommand @Inject constructor() : IServiceCommand {

    @Inject @ForApplication lateinit var context: android.content.Context
    @Inject lateinit var messageManager: IMessageManager
    @Inject lateinit var prefs: android.content.SharedPreferences
    @Inject lateinit var bus: Bus
    @Inject lateinit var navigator: Navigator

    private val tag = "PB-PebbleServiceCommand"
    private var status: BaseStatus.Status = BaseStatus.Status.NOT_INITIALIZED

    @Subscribe
    fun onNewLocationEvent(newLocation: NewLocation) {
        sendLocationToPebble(newLocation)
    }

    @Subscribe
    fun onGPSServiceState(event: GPSStatus) {
        when (event.status) {
            BaseStatus.Status.STARTED -> {
                messageManager.showWatchFace()
                notifyPebbleGPSStarted()
            }
            BaseStatus.Status.STOPPED -> notifyPebbleGPSStopped()
            BaseStatus.Status.DISABLED -> notifyPebbleGPSDisable()
            else -> {}
        }
    }

    @Subscribe
    fun onNewMessageEvent(message: NewMessage) {
        messageManager.showSimpleNotificationOnWatch("KayPS", message.message)
    }

    @Subscribe
    fun onHrMonitorEnable(event: HrMonitorEnable) {
        sendHrMonitorEnable(event.getEnabled())
    }

    private fun sendHrMonitorEnable(enabled: Int) {
        val data = java.util.HashMap<UInt, io.rebble.pebblekit2.common.model.PebbleDictionaryItem>()
        data[Constants.PEBBLE_MSG_HR_MONITOR_ENABLE.toUInt()] =
            io.rebble.pebblekit2.common.model.PebbleDictionaryItem.Int32(enabled)
        messageManager.offer(data)
    }

    override fun execute(container: IInjectionContainer) {
        container.inject(this)
        bus.register(this)
        status = BaseStatus.Status.INITIALIZED
    }

    override fun dispose() {
        bus.unregister(this)
    }

    override fun getStatus(): BaseStatus.Status = status

    private fun notifyPebbleGPSStarted() {
        val dict = android.util.Pair(Constants.STATE_CHANGED, Constants.STATE_START)
        sendState(dict)
        // Always (re)push the HR-monitor setting so the watch enables its sensor even if the
        // user toggled the preference while no GPS session was running (and the bus event was missed).
        sendHrMonitorEnable(if (prefs.getBoolean(Constants.PREF_PEBBLE_HRM, false)) 1 else 0)
    }

    private fun notifyPebbleGPSStopped() {
        sendState(android.util.Pair(Constants.STATE_CHANGED, Constants.STATE_STOP))
    }

    private fun notifyPebbleGPSDisable() {
        messageManager.showSimpleNotificationOnWatch("KayPS", "GPS is disabled on your phone. Please enable it.")
    }

    private fun sendState(pair: android.util.Pair<Int, Int>) {
        val data = java.util.HashMap<UInt, io.rebble.pebblekit2.common.model.PebbleDictionaryItem>()
        data[pair.first.toUInt()] = io.rebble.pebblekit2.common.model.PebbleDictionaryItem.Int32(pair.second)
        messageManager.offer(data)
    }

    private fun sendLocationToPebble(newLocation: NewLocation) {
        val data = buildLocationDictionary(
            newLocation,
            navigator,
            true,
            prefs.getBoolean("PREF_DEBUG", false),
            prefs.getBoolean("LIVE_TRACKING", false),
            prefs.getString("REFRESH_INTERVAL", Constants.REFRESH_INTERVAL_DEFAULT.toString())!!.toInt(),
            prefs.getInt("WATCHFACE_VERSION", 0),
            prefs.getBoolean("NAV_NOTIFICATION", false)
        )
        messageManager.offerIfLow(data, 5)
    }
}
