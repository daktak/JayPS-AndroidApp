package com.njackson.activityrecognition;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.njackson.Constants;
import com.njackson.application.IInjectionContainer;
import com.njackson.application.modules.ForApplication;
import com.njackson.events.ActivityRecognitionCommand.ActivityRecognitionChangeState;
import com.njackson.events.ActivityRecognitionCommand.ActivityRecognitionStatus;
import com.njackson.events.ActivityRecognitionCommand.NewActivityEvent;
import com.njackson.events.GPSServiceCommand.GPSChangeState;
import com.njackson.events.base.BaseStatus;
import com.njackson.service.IServiceCommand;
import com.njackson.utils.services.IServiceStarter;
import com.njackson.utils.time.ITimer;
import com.njackson.utils.time.ITimerHandler;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import javax.inject.Inject;

/**
 * Created by njackson on 01/01/15.
 */
public class ActivityRecognitionServiceCommand implements IServiceCommand, ITimerHandler {

    private static final String TAG = "PB-ActivityRecognitionService";

    @Inject Bus _bus;
    @Inject IServiceStarter _serviceStarter;
    @Inject ITimer _timer;
    @Inject SharedPreferences _sharedPreferences;
    @Inject @ForApplication Context _applicationContext;

    public static final int MILLISECONDS_PER_SECOND = 1000;
    public static final int DETECTION_INTERVAL_SECONDS = 2;
    public static final int DETECTION_INTERVAL_MILLISECONDS = MILLISECONDS_PER_SECOND * DETECTION_INTERVAL_SECONDS;

    private PendingIntent _activityRecognitionPendingIntent;
    private ActivityRecognitionClient _recognitionClient;
    private BaseStatus.Status _currentStatus = BaseStatus.Status.NOT_INITIALIZED;

    int _lastActivity = -1;
    int _nbStart = 0;
    int _nbStop = 0;
    boolean _gpsStarted = false;

    @Subscribe
    public void onNewActivityEvent(NewActivityEvent event) {
        boolean autoStart = _sharedPreferences.getBoolean("ACTIVITY_RECOGNITION", false);

        if (autoStart) {
            boolean start = false;
            boolean stop = false;

            switch(event.getActivity().getMostProbableActivity().getType()) {
                case DetectedActivity.IN_VEHICLE:
                case DetectedActivity.STILL:
                    stop = true;
                    break;
                case DetectedActivity.ON_BICYCLE:
                    start = true;
                    break;
                case DetectedActivity.ON_FOOT:
                case DetectedActivity.WALKING:
                case DetectedActivity.RUNNING:
                    if (_sharedPreferences.getBoolean("ACTIVITY_RECOGNITION_WALKING", false)) {
                        start = true;
                    } else {
                        stop = true;
                    }
                    break;

                case DetectedActivity.UNKNOWN:
                case DetectedActivity.TILTING:
                default:
                    break;
            }
            if (start) {
                if (_nbStop > 0) {
                    _timer.cancel();
                }
                _nbStop = 0;
                _nbStart++;
                if (!_timer.getActive()) {
                    _timer.setTimer(Constants.ACTIVITY_RECOGNITION_MOVE_TIME * MILLISECONDS_PER_SECOND, this);
                }
            }
            if (stop) {
                if (_nbStart > 0) {
                    _timer.cancel();
                }
                _nbStart = 0;
                _nbStop++;
                if (!_timer.getActive()) {
                    _timer.setTimer(Constants.ACTIVITY_RECOGNITION_STILL_TIME * MILLISECONDS_PER_SECOND, this);
                }
            }
            if (_lastActivity != event.getActivity().getMostProbableActivity().getType()) {
                _lastActivity = event.getActivity().getMostProbableActivity().getType();
                Log.d(TAG, "_lastActivity: " + _lastActivity);
            }
        }
    }

    @Subscribe
    public void onChangeState(ActivityRecognitionChangeState event) {
        switch (event.getState()) {
            case START:
                if(_currentStatus != BaseStatus.Status.STARTED) {
                    start();
                }
                break;
            case STOP:
                if(_currentStatus != BaseStatus.Status.STOPPED) {
                    stop();
                }
                break;
        }
    }

    @Subscribe
    public void onGPSChangeState(GPSChangeState event) {
        switch(event.getState()) {
            case START:
                _gpsStarted = true;
                break;
            case STOP:
                _gpsStarted = false;
                break;
        }
    }

    @Override
    public void execute(IInjectionContainer container) {
        container.inject(this);
        _bus.register(this);
        _currentStatus = BaseStatus.Status.INITIALIZED;
    }

    @Override
    public void dispose() {
        _bus.unregister(this);
    }

    @Override
    public BaseStatus.Status getStatus() {
        return _currentStatus;
    }

    public void start() {
        Log.d(TAG,"Started Activity Recognition Service");

        _recognitionClient = ActivityRecognition.getClient(_applicationContext);
        createIntentService();
        _recognitionClient.requestActivityUpdates(DETECTION_INTERVAL_MILLISECONDS, _activityRecognitionPendingIntent);

        _currentStatus = BaseStatus.Status.STARTED;
        _bus.post(new ActivityRecognitionStatus(_currentStatus));
    }

    public void stop (){
        Log.d(TAG,"Destroy Activity Recognition Service");

        if (_recognitionClient != null) {
            _recognitionClient.removeActivityUpdates(_activityRecognitionPendingIntent);
        }

        _currentStatus = BaseStatus.Status.STOPPED;
        _bus.post(new ActivityRecognitionStatus(_currentStatus));
    }

    private void createIntentService() {
        Intent i = new Intent(_applicationContext, ActivityRecognitionIntentService.class);
        _activityRecognitionPendingIntent = PendingIntent.getService(_applicationContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void handleTimeout() {
        Log.d(TAG, "_gpsStarted:" + _gpsStarted);

        if (_nbStart > 0) {
            Log.d(TAG, "Starting location + _nbStart:" + _nbStart);
            if (!_gpsStarted) {
                _serviceStarter.startLocationServices();
            }
        } else if (_nbStop > 0) {
            Log.d(TAG, "Stopping location + _nbStop:" + _nbStop);
            if (_gpsStarted) {
                _serviceStarter.stopLocationServices();
            }
        } else {
            Log.d(TAG, "Error handleTimeout");
        }
        _nbStart = _nbStop = 0;
    }
}
