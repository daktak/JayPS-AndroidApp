package com.njackson.utils.version

import android.content.Context

class PebbleVersion : IWatchFaceVersion {
    override fun getFirmwareVersion(context: Context?): String {
        // PebbleKit.getWatchFWVersion is gone in pebblekit2. The companion app
        // (CoreApp / microPebble) manages the watch firmware; return empty so the
        // install URL simply omits the firmware param.
        return ""
    }
}
