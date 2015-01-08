package com.njackson.fit;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.RecordingApi;
import com.google.android.gms.fitness.data.DataType;
import com.njackson.application.PebbleBikeApplication;
import com.njackson.events.status.GoogleFitStatus;
import com.squareup.otto.Bus;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Created by njackson on 05/01/15.
 */
public class GoogleFitService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "GoogleFitService";
    @Inject Bus _bus;
    @Inject @Named("GoogleFit") GoogleApiClient _googleAPIClient;
    @Inject RecordingApi _recordingApi;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        // ensures that if the service is recycled then it is restarted with the same refresh interval
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ((PebbleBikeApplication)getApplication()).inject(this);
        _bus.register(this);

        _googleAPIClient.registerConnectionCallbacks(this);
        _googleAPIClient.registerConnectionFailedListener(this);
        _googleAPIClient.connect();
    }

    @Override
    public void onDestroy (){
        _bus.unregister(this);

        _googleAPIClient.unregisterConnectionFailedListener(this);
        _googleAPIClient.unregisterConnectionCallbacks(this);

        _recordingApi.unsubscribe(_googleAPIClient, DataType.TYPE_DISTANCE_DELTA);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleCommand(Intent intent) {
        _bus.post(new GoogleFitStatus(GoogleFitStatus.State.SERVICE_STARTED));
    }

    /* GOOOGLE CLIENT DELEGATE METHODS */
    @Override
    public void onConnected(Bundle bundle) {
        setupRecordingApi();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
    /* END GOOGLE CLIENT DELEGATE METHODS */

    private void setupRecordingApi() {
        _recordingApi.subscribe(_googleAPIClient, DataType.TYPE_DISTANCE_DELTA).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    if (status.getStatusCode()
                            == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                        Log.i(TAG, "Existing subscription for activity detected.");
                    } else {
                        Log.i(TAG, "Successfully subscribed!");
                    }
                } else {
                    Log.i(TAG, "There was a problem subscribing.");
                }
            }
        });
    }
}
