package com.njackson.pebble

import io.rebble.pebblekit2.common.model.PebbleDictionary

interface IMessageManager {
    fun offer(data: PebbleDictionary): Boolean
    fun offerIfLow(data: PebbleDictionary, sizeMax: Int): Boolean

    fun showWatchFace()
    fun hideWatchFace()

    fun showSimpleNotificationOnWatch(title: String, text: String)
    fun sendMessageToPebble(title: String, message: String)

    fun sendSavedDataToPebble(
        isLocationServicesRunning: Boolean,
        units: Int,
        distance: Float,
        elapsedTime: Long,
        ascent: Float,
        maxSpeed: Float
    )
}
