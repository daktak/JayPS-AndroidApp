package com.njackson.test.adapters

import android.test.AndroidTestCase
import com.njackson.Constants
import com.njackson.adapters.buildLocationDictionary
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.gps.Navigator
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

/**
 * Guards the byte layout sent to the pebblebike watchface (src/c/communication.c).
 * The BYTE_* offsets must stay identical to that C decoder.
 */
class PebbleBinaryTest : AndroidTestCase() {

    private val BYTE_SETTINGS = 0
    private val BYTE_ACCURACY = 1
    private val BYTE_DISTANCE1 = 2
    private val BYTE_DISTANCE2 = 3
    private val BYTE_TIME1 = 4
    private val BYTE_TIME2 = 5
    private val BYTE_ALTITUDE1 = 6
    private val BYTE_ALTITUDE2 = 7
    private val BYTE_ASCENT1 = 8
    private val BYTE_ASCENT2 = 9
    private val BYTE_ASCENTRATE1 = 10
    private val BYTE_ASCENTRATE2 = 11
    private val BYTE_SLOPE = 12
    private val BYTE_XPOS1 = 13
    private val BYTE_XPOS2 = 14
    private val BYTE_YPOS1 = 15
    private val BYTE_YPOS2 = 16
    private val BYTE_SPEED1 = 17
    private val BYTE_SPEED2 = 18
    private val BYTE_BEARING = 19
    private val BYTE_HEARTRATE = 20
    private val BYTE_MAXSPEED1 = 21
    private val BYTE_CADENCE = 23
    private val BYTE_POWER1 = 24
    private val BYTE_POWER2 = 25
    private val BYTE_AVGPOWER = 26
    private val BYTE_MAXPOWER1 = 27
    private val BYTE_MAXPOWER2 = 28
    private val BYTE_AVGPWR3 = 29
    private val BYTE_NPPWR30 = 30

    private lateinit var data: ByteArray

    private fun build(event: NewLocation): ByteArray {
        val dict = buildLocationDictionary(
            event, Navigator(), true, true, true, 5000,
            Constants.MIN_VERSION_PEBBLE_FOR_LOCATION_DATA_V3, true
        )
        val item = dict[Constants.PEBBLE_LOCATION_DATA_V3.toUInt()] as PebbleDictionaryItem.Bytes
        return item.value
    }

    override fun setUp() {
        super.setUp()
        val event = NewLocation()
        event.setUnits(1)
        event.setBearing(190.1)
        event.setYpos(2.2)
        event.setXpos(3.3)
        event.setElapsedTimeSeconds(423)
        event.setAvgSpeed(5.5f)
        event.setAccuracy(6.6f)
        event.setAltitude(700.7)
        event.setAscent(38.8)
        event.setAscentRate(9.9f)
        event.setDistance(10.1f)
        event.setLatitude(11.1)
        event.setLongitude(12.2)
        event.setSlope(13.3f)
        event.setSpeed(14.4f)
        event.setHeartRate(123)
        event.setCyclingCadence(134)
        data = build(event)
    }

    private fun bitIsSet(b: Byte, position: Int): Boolean {
        return (b.toInt() shr position and 1) == 1
    }

    fun testConvertsUnitsCorrectly() {
        assertTrue("Expected units bit to be true", bitIsSet(data[BYTE_SETTINGS], 0))
    }

    fun testServiceRunningCorrectly() {
        assertTrue("Expected service running bit to be true", bitIsSet(data[BYTE_SETTINGS], 3))
    }

    fun testDebugCorrectly() {
        assertTrue("Expected debug bit to be true", bitIsSet(data[BYTE_SETTINGS], 4))
    }

    fun testLiveTracking() {
        assertTrue("Expected live tracking bit to be true", bitIsSet(data[BYTE_SETTINGS], 5))
    }

    fun testRefresh() {
        assertEquals("Expected refresh of 3", 3, ((data[BYTE_SETTINGS].toInt() shr 6) + 4) % 4)
    }

    fun testAccuracy() {
        assertEquals("Accuracy: Expected value 7", 7, data[BYTE_ACCURACY].toInt())
    }

    fun testDistance1() {
        assertEquals("Distance 1: Expected value -14", -14, data[BYTE_DISTANCE1].toInt())
    }

    fun testDistance2() {
        assertEquals("Distance 2: Expected value 3", 3, data[BYTE_DISTANCE2].toInt())
    }

    fun testTime1() {
        assertEquals("Time 1: Expected value -89", -89, data[BYTE_TIME1].toInt())
    }

    fun testTime2() {
        assertEquals("Time 2: Expected value 1", 1, data[BYTE_TIME2].toInt())
    }

    fun testAltitude1() {
        assertEquals("Altitude 1: Expected value -68", -68, data[BYTE_ALTITUDE1].toInt())
    }

    fun testAltitude2() {
        assertEquals("Altitude 2: Expected value 2", 2, data[BYTE_ALTITUDE2].toInt())
    }

    fun testAscent1() {
        assertEquals("Ascent 1: Expected value 38", 38, data[BYTE_ASCENT1].toInt())
    }

    fun testAscent2() {
        assertEquals("Ascent 2: Expected value 0", 0, data[BYTE_ASCENT2].toInt())
    }

    fun testAscentRate1() {
        assertEquals("Ascent Rate 1: Expected value 9", 9, data[BYTE_ASCENTRATE1].toInt())
    }

    fun testAscentRate2() {
        assertEquals("Ascent Rate 2: Expected value 0", 0, data[BYTE_ASCENTRATE2].toInt())
    }

    fun testSlope() {
        assertEquals("Slope: Expected value 13", 13, data[BYTE_SLOPE].toInt())
    }

    fun testXpos1() {
        assertEquals("Xpos 1: Expected value 3", 3, data[BYTE_XPOS1].toInt())
    }

    fun testXpos2() {
        assertEquals("Xpos 2: Expected value 0", 0, data[BYTE_XPOS2].toInt())
    }

    fun testYPos1() {
        assertEquals("Ypos 1: Expected value 2", 2, data[BYTE_YPOS1].toInt())
    }

    fun testYPos2() {
        assertEquals("Ypos 2: Expected value 0", 0, data[BYTE_YPOS2].toInt())
    }

    fun testSpeed1() {
        assertEquals("Speed 1: Expected value -112", -112, data[BYTE_SPEED1].toInt())
    }

    fun testSpeed2() {
        assertEquals("Speed 2: Expected value 0", 0, data[BYTE_SPEED2].toInt())
    }

    fun testBearing() {
        assertEquals("Bearing: Expected value -121", -121, data[BYTE_BEARING].toInt())
    }

    fun testHeartrate() {
        assertEquals("Heartrate: Expected value 123", 123, data[BYTE_HEARTRATE].toInt())
    }

    fun testCyclingCadence() {
        assertEquals("Cycling Cadence: Expected value 134", 134, data[BYTE_CADENCE].toInt() and 0xFF)
    }

    fun testPowerBytes() {
        val event = NewLocation()
        event.setPower(200)
        event.setAvgPower(80)
        event.setMaxPower(300)
        event.setAvgPower3(90)
        event.setNormalizedPower(85)
        val bytes = build(event)
        assertEquals(200, (bytes[BYTE_POWER1].toInt() and 0xFF) + (bytes[BYTE_POWER2].toInt() and 0xFF) * 256)
        assertEquals(80, bytes[BYTE_AVGPOWER].toInt() and 0xFF)
        assertEquals(300, (bytes[BYTE_MAXPOWER1].toInt() and 0xFF) + (bytes[BYTE_MAXPOWER2].toInt() and 0xFF) * 256)
        assertEquals(90, bytes[BYTE_AVGPWR3].toInt() and 0xFF)
        assertEquals(85, bytes[BYTE_NPPWR30].toInt() and 0xFF)
    }
}
