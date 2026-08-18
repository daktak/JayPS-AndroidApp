package com.njackson.application

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.njackson.activities.MainActivity
import com.njackson.activities.SettingsActivity
import com.njackson.activityrecognition.ActivityRecognitionIntentService
import com.njackson.activityrecognition.ActivityRecognitionServiceCommand
import com.njackson.application.modules.AndroidModule
import com.njackson.fragments.AltitudeFragment
import com.njackson.fragments.SpeedFragment
import com.njackson.fragments.StartButtonFragment
import com.njackson.gps.GPSServiceCommand
import com.njackson.live.LiveServiceCommand
import com.njackson.oruxmaps.OruxMapsServiceCommand
import com.njackson.pebble.PebbleListenerService
import com.njackson.pebble.PebbleServiceCommand
import com.njackson.sensor.BLEServiceCommand
import com.njackson.sensor.Ble
import com.njackson.service.MainService
import com.njackson.upload.StravaUpload
import com.njackson.utils.BootUpReceiver
import javax.inject.Inject

class PebbleBikeApplication : Application(), IInjectionContainer {

    private val tag = "PB-PebbleApp"

    @Inject lateinit var sharedPreferences: SharedPreferences

    lateinit var component: AppComponent

    override fun onCreate() {
        super.onCreate()
        component = DaggerAppComponent.builder()
            .androidModule(AndroidModule(this))
            .build()
        inject(this)
    }

    override fun onLowMemory() {
        Log.d(tag, "Low Memory")
        super.onLowMemory()
    }

    override fun inject(obj: Any) {
        when (obj) {
            is MainActivity -> component.inject(obj)
            is SettingsActivity -> component.inject(obj)
            is StartButtonFragment -> component.inject(obj)
            is SpeedFragment -> component.inject(obj)
            is AltitudeFragment -> component.inject(obj)
            is MainService -> component.inject(obj)
            is ActivityRecognitionIntentService -> component.inject(obj)
            is BootUpReceiver -> component.inject(obj)
            is LiveServiceCommand -> component.inject(obj)
            is OruxMapsServiceCommand -> component.inject(obj)
            is BLEServiceCommand -> component.inject(obj)
            is Ble -> component.inject(obj)
            is PebbleServiceCommand -> component.inject(obj)
            is PebbleListenerService -> component.inject(obj)
            is GPSServiceCommand -> component.inject(obj)
            is StravaUpload -> component.inject(obj)
            is ActivityRecognitionServiceCommand -> component.inject(obj)
            is PebbleBikeApplication -> component.inject(obj)
            else -> throw IllegalArgumentException("Cannot inject ${obj.javaClass}")
        }
    }
}
