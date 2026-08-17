package com.njackson.adapters

import com.njackson.Constants
import com.njackson.events.LiveServiceCommand.LiveMessage
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

data class LiveBuildResult(val data: PebbleDictionary, val forceSend: Boolean)

/**
 * Builds the AppMessage dictionary for live-tracking friends.
 * Mirrors the old LiveMessageToPebbleDictionary byte layout (MSG_LIVE_NAME*, MSG_LIVE_SHORT).
 */
fun buildLiveDictionary(message: LiveMessage): LiveBuildResult {
    val dict = mutableMapOf<UInt, PebbleDictionaryItem>()
    var forceSend = false

    val live = message.live
    if (live.isNotEmpty()) {
        message.getName0()?.let { dict[Constants.MSG_LIVE_NAME0.toUInt()] = PebbleDictionaryItem.Text(it) }
        message.getName1()?.let { dict[Constants.MSG_LIVE_NAME1.toUInt()] = PebbleDictionaryItem.Text(it) }
        message.getName2()?.let { dict[Constants.MSG_LIVE_NAME2.toUInt()] = PebbleDictionaryItem.Text(it) }
        message.getName3()?.let { dict[Constants.MSG_LIVE_NAME3.toUInt()] = PebbleDictionaryItem.Text(it) }
        message.getName4()?.let { dict[Constants.MSG_LIVE_NAME4.toUInt()] = PebbleDictionaryItem.Text(it) }
        dict[Constants.MSG_LIVE_SHORT.toUInt()] = PebbleDictionaryItem.Bytes(live)
        forceSend = true
    }

    return LiveBuildResult(dict, forceSend)
}
