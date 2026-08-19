package com.njackson.pebble

import com.njackson.Constants
import com.njackson.adapters.buildLiveDictionary
import com.njackson.adapters.buildLocationDictionary
import com.njackson.application.IInjectionContainer
import com.njackson.events.GPSServiceCommand.GPSStatus
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.events.LiveServiceCommand.LiveMessage
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
    fun onLiveMessage(msg: LiveMessage) {
        sendLiveMessage(msg)
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

    private fun sendLiveMessage(message: LiveMessage) {
        val result = buildLiveDictionary(message)
        if (result.forceSend) messageManager.offer(result.data)
        else messageManager.offerIfLow(result.data, 5)
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
