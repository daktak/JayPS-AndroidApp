package com.njackson.application;

import com.njackson.activities.MainActivity;
import com.njackson.activities.SettingsActivity;
import com.njackson.activityrecognition.ActivityRecognitionIntentService;
import com.njackson.activityrecognition.ActivityRecognitionServiceCommand;
import com.njackson.fragments.AltitudeFragment;
import com.njackson.fragments.SpeedFragment;
import com.njackson.fragments.StartButtonFragment;
import com.njackson.gps.GPSServiceCommand;
import com.njackson.live.LiveServiceCommand;
import com.njackson.oruxmaps.OruxMapsServiceCommand;
import com.njackson.pebble.PebbleListenerService;
import com.njackson.pebble.PebbleServiceCommand;
import com.njackson.sensor.BLEServiceCommand;
import com.njackson.sensor.Ble;
import com.njackson.service.MainService;
import com.njackson.utils.BootUpReceiver;

import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = com.njackson.application.modules.AndroidModule.class)
public interface AppComponent {
    void inject(MainActivity a);
    void inject(SettingsActivity a);
    void inject(StartButtonFragment a);
    void inject(SpeedFragment a);
    void inject(AltitudeFragment a);
    void inject(MainService a);
    void inject(ActivityRecognitionIntentService a);
    void inject(BootUpReceiver a);
    void inject(LiveServiceCommand a);
    void inject(OruxMapsServiceCommand a);
    void inject(BLEServiceCommand a);
    void inject(Ble a);
    void inject(PebbleServiceCommand a);
    void inject(PebbleListenerService a);
    void inject(GPSServiceCommand a);
    void inject(ActivityRecognitionServiceCommand a);
    void inject(PebbleBikeApplication a);
}
