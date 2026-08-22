package com.njackson.test.pebble

import android.content.Context
import android.content.SharedPreferences
import android.test.AndroidTestCase
import com.njackson.Constants
import com.njackson.events.GPSServiceCommand.GPSStatus
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.events.PebbleServiceCommand.NewMessage
import com.njackson.events.base.BaseStatus
import com.njackson.gps.Navigator
import com.njackson.pebble.IMessageManager
import com.njackson.pebble.PebbleServiceCommand
import io.rebble.pebblekit2.common.model.PebbleDictionary
import com.squareup.otto.Bus
import com.squareup.otto.ThreadEnforcer
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

@Suppress("UNCHECKED_CAST")
fun <T> anyValue(): T {
    org.mockito.ArgumentMatchers.any<T>()
    return null as T
}

class PebbleServiceCommandTest : AndroidTestCase() {

    private lateinit var command: PebbleServiceCommand
    private lateinit var messageManager: IMessageManager
    private lateinit var prefs: SharedPreferences
    private lateinit var bus: Bus

    override fun setUp() {
        super.setUp()
        messageManager = mock(IMessageManager::class.java)
        prefs = mock(SharedPreferences::class.java)
        bus = Bus(ThreadEnforcer.ANY)
        command = PebbleServiceCommand()
        command.messageManager = messageManager
        command.prefs = prefs
        command.bus = bus
        command.navigator = Navigator()
        command.context = mock(Context::class.java)
        `when`(prefs.getBoolean("PREF_DEBUG", false)).thenReturn(true)
        `when`(prefs.getBoolean("LIVE_TRACKING", false)).thenReturn(true)
        `when`(prefs.getString(eq("REFRESH_INTERVAL"), anyString())).thenReturn("1000")
        `when`(prefs.getInt("WATCHFACE_VERSION", 0)).thenReturn(Constants.MIN_VERSION_PEBBLE_FOR_LOCATION_DATA_V3)
        `when`(prefs.getBoolean("NAV_NOTIFICATION", false)).thenReturn(false)
        bus.register(command)
    }

    override fun tearDown() {
        bus.unregister(command)
        super.tearDown()
    }

    fun testServiceShowsWatchFaceOnGPSServiceStart() {
        bus.post(GPSStatus(BaseStatus.Status.STARTED))
        verify(messageManager, timeout(1000)).showWatchFace()
    }

    fun testUpdatePebbleGPSServiceStop() {
        bus.post(GPSStatus(BaseStatus.Status.STOPPED))
        verify(messageManager, timeout(1000)).offer(anyValue())
    }

    fun testSendsLocationToPebble() {
        val event = NewLocation()
        event.setUnits(0)
        event.setSpeed(45.4f)
        event.setTime(1420988759)
        bus.post(event)
        verify(messageManager, timeout(2000)).offerIfLow(anyValue(), eq(5))
    }

    fun testNewMessageEventSendsMessageToPebble() {
        bus.post(NewMessage("A Message"))
        verify(messageManager, timeout(1000)).showSimpleNotificationOnWatch("KayPS", "A Message")
    }
}
