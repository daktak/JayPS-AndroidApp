package com.njackson.adapters

import com.njackson.Constants
import com.njackson.events.GPSServiceCommand.NewLocation
import com.njackson.gps.Navigator
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

internal fun putUInt8(d: ByteArray, i: Int, v: Int) {
    d[i] = (v % 256).toByte()
}

internal fun putUInt16(d: ByteArray, i: Int, v: Int) {
    d[i] = (v % 256).toByte()
    d[i + 1] = (v / 256).toByte()
}

internal fun putInt16(d: ByteArray, i: Int, v: Int) {
    d[i] = (Math.abs(v % 256)).toByte()
    d[i + 1] = ((Math.abs(v) / 256) % 128).toByte()
    if (v < 0) d[i + 1] = (d[i + 1] + 128).toByte()
}

internal fun putInt8(d: ByteArray, i: Int, v: Int) {
    d[i] = (Math.abs(v % 128)).toByte()
    if (v < 0) d[i + 1] = (d[i + 1] + 128).toByte()
}

private var lastWatchHr: Int = 255
private var lastWatchCad: Int = 255

/**
 * Builds the AppMessage dictionary sent to the pebblebike watchface for a location update.
 * The byte layout (BYTE_* offsets, uint8/int16 packing) MUST stay identical to
 * src/c/communication.c so the watchface keeps decoding it correctly.
 */
fun buildLocationDictionary(
    event: NewLocation,
    navigator: Navigator,
    serviceRunning: Boolean,
    debug: Boolean,
    liveTrackingEnabled: Boolean,
    refreshInterval: Int,
    watchfaceVersion: Int,
    navNotification: Boolean
): PebbleDictionary {
    val POS_UNITS = 0
    val POS_SERVICE_RUNNING = 3
    val POS_DEBUG = 4
    val POS_LIVETRACKING = 5
    val POS_REFRESH = 6

    val BYTE_SETTINGS = 0
    val BYTE_ACCURACY = 1
    val BYTE_DISTANCE1 = 2
    val BYTE_TIME1 = 4
    val BYTE_ALTITUDE1 = 6
    val BYTE_ASCENT1 = 8
    val BYTE_ASCENTRATE1 = 10
    val BYTE_SLOPE = 12
    val BYTE_XPOS1 = 13
    val BYTE_YPOS1 = 15
    val BYTE_SPEED1 = 17
    val BYTE_BEARING = 19
    val BYTE_HEARTRATE = 20
    val BYTE_MAXSPEED1 = 21
    val BYTE_CADENCE = 23
    val BYTE_POWER1 = 24
    val BYTE_POWER2 = 25
    val BYTE_AVGPOWER = 26
    val BYTE_MAXPOWER1 = 27
    val BYTE_MAXPOWER2 = 28
    val BYTE_AVGPWR3 = 29
    val BYTE_NPPWR30 = 30

    val NAV_BYTE_DISTANCE1 = 0
    val NAV_BYTE_DTD1 = 2
    val NAV_BYTE_BEARING = 4
    val NAV_BYTE_ERROR = 5
    val NAV_BYTE_NB_PAGES = 6
    val NAV_BYTE_PAGE_NUMBER = 7
    val NAV_BYTE_NEXT_INDEX1 = 8
    val NAV_BYTE_SETTINGS = 9
    val NAV_BYTES_POINTS = 10
    val NAV_POS_NOTIFICATION = 7

    val NAV_NB_POINTS = 20
    val NAV_NB_BYTES = NAV_BYTES_POINTS + 4 * NAV_NB_POINTS
    val NB_POINTS_PER_PAGE = 5

    var locationDataVersion = Constants.PEBBLE_LOCATION_DATA_V2
    if (watchfaceVersion >= Constants.MIN_VERSION_PEBBLE_FOR_LOCATION_DATA_V3) {
        locationDataVersion = Constants.PEBBLE_LOCATION_DATA_V3
    }

    if (!serviceRunning) { lastWatchCad = 255 }
    val data = ByteArray(33)

    data[BYTE_SETTINGS] = ((event.units % 8) shl POS_UNITS).toByte()
    data[BYTE_SETTINGS] = (data[BYTE_SETTINGS] + ((if (serviceRunning) 1 else 0) shl POS_SERVICE_RUNNING)).toByte()
    data[BYTE_SETTINGS] = (data[BYTE_SETTINGS] + ((if (debug) 1 else 0) shl POS_DEBUG)).toByte()
    data[BYTE_SETTINGS] = (data[BYTE_SETTINGS] + ((if (liveTrackingEnabled) 1 else 0) shl POS_LIVETRACKING)).toByte()

    var refreshCode = 1
    if (refreshInterval < 1000) refreshCode = 0
    else if (refreshInterval >= 5000) refreshCode = 3
    else if (refreshInterval > 1000) refreshCode = 2
    data[BYTE_SETTINGS] = (data[BYTE_SETTINGS] + ((refreshCode % 4) shl POS_REFRESH)).toByte()

    putUInt8(data, BYTE_ACCURACY, Math.ceil(event.accuracy.toDouble()).toInt())
    putUInt16(data, BYTE_DISTANCE1, Math.floor(100 * event.distance.toDouble()).toInt())
    putUInt16(data, BYTE_TIME1, event.elapsedTimeSeconds)
    putUInt16(data, BYTE_ALTITUDE1, event.altitude.toInt())
    putInt16(data, BYTE_ASCENT1, event.ascent.toInt())
    putInt16(data, BYTE_ASCENTRATE1, event.ascentRate.toInt())
    putInt8(data, BYTE_SLOPE, event.slope.toInt())
    putInt16(data, BYTE_XPOS1, event.xpos.toInt())
    putInt16(data, BYTE_YPOS1, event.ypos.toInt())
    putUInt16(data, BYTE_SPEED1, Math.floor(10 * event.speed.toDouble()).toInt())
    putUInt8(data, BYTE_BEARING, (event.bearing / 360 * 256).toInt())

    if (locationDataVersion >= Constants.PEBBLE_LOCATION_DATA_V3) {
        val hrToSend = when {
            event.heartRate in 1..254 -> { lastWatchHr = event.heartRate; event.heartRate }
            lastWatchHr in 1..254 -> lastWatchHr
            else -> 255
        }
        putUInt8(data, BYTE_HEARTRATE, hrToSend)
        val cadToSend = when {
            event.cyclingCadence in 1..254 -> { lastWatchCad = event.cyclingCadence; event.cyclingCadence }
            event.cyclingCadence == 0 && event.speed < 0.5f -> 0
            lastWatchCad in 1..254 -> lastWatchCad
            else -> 255
        }
        if (cadToSend == 0) putUInt8(data, BYTE_CADENCE, 0) else putUInt8(data, BYTE_CADENCE, cadToSend)
    } else if (event.cyclingCadence < 255) {
        // On V2 the HR byte is reused for cadence; never overwrite it with the echoed HR.
        putUInt8(data, BYTE_HEARTRATE, event.cyclingCadence)
    }

    putUInt16(data, BYTE_MAXSPEED1, Math.floor(10 * event.maxSpeed.toDouble()).toInt())

    if (event.power >= 0) {
        putUInt16(data, BYTE_POWER1, event.power)
        putUInt8(data, BYTE_AVGPOWER, event.avgPower)
        putUInt16(data, BYTE_MAXPOWER1, event.maxPower)
        putUInt8(data, BYTE_AVGPWR3, event.avgPower3)
        putUInt8(data, BYTE_NPPWR30, event.normalizedPower)
    }

    val dict = mutableMapOf<UInt, PebbleDictionaryItem>()
    dict[locationDataVersion.toUInt()] = PebbleDictionaryItem.Bytes(data)

    if (locationDataVersion >= Constants.PEBBLE_LOCATION_DATA_V3 && event.temperature != 0.0) {
        dict[Constants.PEBBLE_MSG_SENSOR_TEMPERATURE.toUInt()] =
            PebbleDictionaryItem.Int16((10 * event.temperature).toInt().toShort())
    }
    if (event.batteryLevel != 0) {
        dict[Constants.MSG_BATTERY_LEVEL.toUInt()] = PebbleDictionaryItem.Int32(event.batteryLevel)
    }
    if (event.heartRateMax != 0) {
        val hm = ByteArray(2)
        putUInt8(hm, 0, event.heartRateMax % 256)
        putUInt8(hm, 1, event.heartRateMode % 256)
        dict[Constants.MSG_HR_MAX.toUInt()] = PebbleDictionaryItem.Bytes(hm)
    }
    if (event.ftp != 0) {
        val f = ByteArray(2)
        putUInt16(f, 0, event.ftp)
        dict[Constants.MSG_FTP.toUInt()] = PebbleDictionaryItem.Bytes(f)
    }
    if (event.sendNavigation) {
        val nav = ByteArray(NAV_NB_BYTES)
        putUInt16(nav, NAV_BYTE_DISTANCE1, Math.floor(navigator.getNextDistance(event.units).toDouble()).toInt())
        putUInt16(nav, NAV_BYTE_DTD1, Math.floor((navigator.getDistanceToDestination(event.units) * 100).toDouble()).toInt())
        putUInt8(nav, NAV_BYTE_BEARING, (navigator.getNextBearing() / 360 * 256).toInt())
        putUInt8(nav, NAV_BYTE_ERROR, Math.floor((Math.abs(navigator.getError()) / 10).toDouble()).toInt())

        val curPageNumber = Math.floor(navigator.getNextIndex() / NB_POINTS_PER_PAGE.toDouble()).toInt()
        val firstPageNumberSent = Math.max(0, curPageNumber - 1)
        val firstIndex = firstPageNumberSent * NB_POINTS_PER_PAGE

        putUInt8(nav, NAV_BYTE_NB_PAGES, Math.ceil(navigator.getNbPoints() / NB_POINTS_PER_PAGE.toDouble()).toInt())
        putUInt8(nav, NAV_BYTE_PAGE_NUMBER, firstPageNumberSent)
        putUInt16(nav, NAV_BYTE_NEXT_INDEX1, navigator.getNextIndex())

        nav[NAV_BYTE_SETTINGS] = (nav[NAV_BYTE_SETTINGS] + ((if (navNotification) 1 else 0) shl NAV_POS_NOTIFICATION)).toByte()

        val firstLocation = event.firstLocation
        for (i in 0 until NAV_NB_POINTS) {
            var xpos = 0xFFFF
            var ypos = 0xFFFF
            val point = navigator.getPoint(firstIndex + i)
            if (point != null && firstLocation != null) {
                val rad = Math.PI / 180.0
                val dist = firstLocation.distanceTo(point)
                val bear = firstLocation.bearingTo(point) * rad
                xpos = Math.floor(dist * Math.sin(bear) / 10).toInt()
                ypos = Math.floor(dist * Math.cos(bear) / 10).toInt()
            }
            putInt16(nav, NAV_BYTES_POINTS + 4 * i, xpos)
            putInt16(nav, NAV_BYTES_POINTS + 2 + 4 * i, ypos)
        }
        dict[Constants.MSG_NAVIGATION.toUInt()] = PebbleDictionaryItem.Bytes(nav)
    }
    return dict
}
