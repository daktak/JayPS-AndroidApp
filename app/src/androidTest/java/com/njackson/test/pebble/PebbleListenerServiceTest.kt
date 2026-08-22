package com.njackson.test.pebble

import android.content.SharedPreferences
import android.test.AndroidTestCase
import com.njackson.Constants
import com.njackson.oruxmaps.IOruxMaps
import com.njackson.pebble.IMessageManager
import com.njackson.pebble.PebbleListenerService
import com.njackson.state.IGPSDataStore
import com.njackson.utils.services.IServiceStarter
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*

class PebbleListenerServiceTest : AndroidTestCase() {

    private lateinit var service: PebbleListenerService
    private lateinit var oruxMaps: IOruxMaps
    private lateinit var messageManager: IMessageManager
    private lateinit var serviceStarter: IServiceStarter
    private lateinit var dataStore: IGPSDataStore
    private lateinit var prefs: SharedPreferences
    private val watch = WatchIdentifier("test")

    override fun setUp() {
        super.setUp()
        service = PebbleListenerService()
        oruxMaps = mock(IOruxMaps::class.java)
        messageManager = mock(IMessageManager::class.java)
        serviceStarter = mock(IServiceStarter::class.java)
        dataStore = mock(IGPSDataStore::class.java)
        prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(prefs.edit()).thenReturn(editor)
        service.oruxMaps = oruxMaps
        service.messageManager = messageManager
        service.serviceStarter = serviceStarter
        service.dataStore = dataStore
        service.prefs = prefs
        `when`(serviceStarter.isLocationServicesRunning).thenReturn(false)
    }

    private fun buttonDict(button: Int): PebbleDictionary {
        val d = mutableMapOf<UInt, PebbleDictionaryItem>()
        d[Constants.CMD_BUTTON_PRESS.toUInt()] = PebbleDictionaryItem.Int32(button)
        return d
    }

    fun testOruxStartRecord() = runBlocking {
        service.onMessageReceived(Constants.WATCH_UUID, buttonDict(Constants.ORUXMAPS_START_RECORD_CONTINUE_PRESS), watch)
        verify(oruxMaps).startRecordNewSegment()
    }

    fun testOruxStopRecord() = runBlocking {
        service.onMessageReceived(Constants.WATCH_UUID, buttonDict(Constants.ORUXMAPS_STOP_RECORD_PRESS), watch)
        verify(oruxMaps).stopRecord()
    }

    fun testOruxNewWaypoint() = runBlocking {
        service.onMessageReceived(Constants.WATCH_UUID, buttonDict(Constants.ORUXMAPS_NEW_WAYPOINT_PRESS), watch)
        verify(oruxMaps).newWaypoint()
    }

    fun testStopPressStopsLocationServices() = runBlocking {
        service.onMessageReceived(Constants.WATCH_UUID, buttonDict(Constants.STOP_PRESS), watch)
        verify(serviceStarter).stopLocationServices()
    }

    fun testPlayPressStartsLocationServices() = runBlocking {
        service.onMessageReceived(Constants.WATCH_UUID, buttonDict(Constants.PLAY_PRESS), watch)
        verify(serviceStarter).startLocationServices()
    }

    fun testWrongUuidIsNacked() = runBlocking {
        val result = service.onMessageReceived(java.util.UUID.randomUUID(), buttonDict(Constants.STOP_PRESS), watch)
        assertEquals("Expected Nack for unknown UUID", ReceiveResult.Nack, result)
        verify(serviceStarter, never()).stopLocationServices()
    }

    fun testVersionPressUpdatesWatchfaceVersionAndSendsData() = runBlocking {
        val d = mutableMapOf<UInt, PebbleDictionaryItem>()
        d[Constants.MSG_VERSION_PEBBLE.toUInt()] = PebbleDictionaryItem.Int32(330)
        val result = service.onMessageReceived(Constants.WATCH_UUID, d, watch)
        assertEquals(ReceiveResult.Ack, result)
        verify(messageManager).sendSavedDataToPebble(anyBoolean(), anyInt(), anyFloat(), anyLong(), anyFloat(), anyFloat())
    }
}
