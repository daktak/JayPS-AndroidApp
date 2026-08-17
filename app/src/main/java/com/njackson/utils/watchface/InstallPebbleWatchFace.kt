package com.njackson.utils.watchface

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.njackson.utils.messages.IMessageMaker
import com.njackson.utils.version.IAndroidVersion
import com.njackson.utils.version.IWatchFaceVersion

class InstallPebbleWatchFace(
    private val androidVersion: IAndroidVersion,
    private val watchFaceVersion: IWatchFaceVersion
) : IInstallWatchFace {

    private val tag = "PB-InstallWatchFace"

    override fun execute(context: Context, messageMaker: IMessageMaker, uriString: String) {
        try {
            context.startActivity(createIntent(context, uriString))
        } catch (ae: ActivityNotFoundException) {
            messageMaker.showMessage(
                context,
                "Unable to install watchface, do you have the latest Pebble companion app installed?"
            )
        }
    }

    fun getDownloadUrl(versionCode: String, pebbleFirmwareVersion: String, uriString: String): Uri {
        var uri = uriString + "?and&v=" + versionCode
        if (pebbleFirmwareVersion.isNotEmpty()) {
            uri += "&p=" + pebbleFirmwareVersion
        }
        return Uri.parse(uri)
    }

    fun createIntent(context: Context, uriString: String): Intent {
        val uri = getDownloadUrl(
            androidVersion.getVersionCode(context),
            watchFaceVersion.getFirmwareVersion(context),
            uriString
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setDataAndType(uri, "application/octet-stream")
        // The Pebble companion app (CoreApp / microPebble) opens PBW files.
        return intent
    }
}
