package com.njackson.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.njackson.R;
import com.njackson.Constants;
import com.njackson.adapters.AdvancedLocationToNewLocation;
import com.njackson.events.BleServiceCommand.BleSensorData;
import com.njackson.events.GPSServiceCommand.GPSStatus;
import com.njackson.events.GPSServiceCommand.MyLocation;
import com.njackson.events.GPSServiceCommand.NewLocation;
import com.njackson.events.GPSServiceCommand.ResetGPSState;
import com.njackson.events.GPSServiceCommand.SavedLocation;
import com.njackson.events.base.BaseStatus;
import com.njackson.state.IGPSDataStore;
import com.njackson.utils.NumberConverter;
import com.njackson.utils.Units;
import com.squareup.otto.Subscribe;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

public class SpeedFragment extends BaseFragment {

    private String TAG = "PB-SpeedFragment";

    @Inject SharedPreferences _sharedPreferences;
    @Inject IGPSDataStore _dataStore;

    private boolean _restoreInstanceState;

    private boolean _hrBle = false;
    private boolean _powerVisible = false;
    private boolean _cadenceVisible = false;
    private SharedPreferences.OnSharedPreferenceChangeListener _prefListener;

    @Subscribe
    public void onResetGPSStateEvent(ResetGPSState event) {
        restoreFromPreferences();
    }

    @Subscribe
    public void onGPSServiceState(GPSStatus event) {
        //Log.d(TAG, "onGPSServiceState:" + event.getStatus().toString());
        if (event.getStatus() == BaseStatus.Status.STOPPED) {
            // GPS is off, get stored values

            AdvancedLocation advancedLocation = new AdvancedLocation();
            advancedLocation.setDistance(_dataStore.getDistance());
            advancedLocation.setElapsedTime(_dataStore.getElapsedTime());

            NewLocation newlocation = new AdvancedLocationToNewLocation(advancedLocation, 0, 0, _dataStore.getMeasurementUnits());
            updateFragment(newlocation);
        }
    }
    @Subscribe
    public void onNewLocation(NewLocation event) {
        //Log.d(TAG, "onNewLocation time:" + event.getElapsedTimeSeconds() + " class:"+event.getClass());
        updateFragment(event);
    }
    @Subscribe
    public void onSavedLocation(SavedLocation event) {
        //Log.d(TAG, "onSavedLocation time:" + event.getElapsedTimeSeconds() + " class:"+event.getClass());
        updateFragment(event);
    }
    @Subscribe
    public void onBleSensorData(BleSensorData event) {
        switch (event.getType()) {
            case BleSensorData.SENSOR_HRM: _hrBle = true; break;
            case BleSensorData.SENSOR_POWER: _powerVisible = true; break;
            case BleSensorData.SENSOR_CSC_CADENCE:
            case BleSensorData.SENSOR_RSC: _cadenceVisible = true; break;
        }
        applyVisibility();
    }

    private void applyVisibility() {
        if (getActivity() == null) return;
        boolean pebbleHrm = _sharedPreferences.getBoolean(Constants.PREF_PEBBLE_HRM, false);
        getActivity().findViewById(R.id.hr_container).setVisibility((pebbleHrm || _hrBle) ? View.VISIBLE : View.GONE);
        getActivity().findViewById(R.id.power_container).setVisibility(_powerVisible ? View.VISIBLE : View.GONE);
        getActivity().findViewById(R.id.cadence_container).setVisibility(_cadenceVisible ? View.VISIBLE : View.GONE);
    }
    private void updateFragment(MyLocation event) {
        NumberConverter converter = new NumberConverter();

        TextView speed = (TextView)getActivity().findViewById(R.id.speed_text);
        String speedText;
        if (Units.isPace(event.getUnits())) {
            speedText = converter.convertSpeedToPace(event.getSpeed());
        } else {
            speedText = converter.convertFloatToString(event.getSpeed(), 1);
        }
        speed.setText(speedText);

        TextView avgSpeed = (TextView)getActivity().findViewById(R.id.avgspeed_text);
        String avgSpeedText;
        if (Units.isPace(event.getUnits())) {
            avgSpeedText = converter.convertSpeedToPace(event.getAverageSpeed());
        } else {
            avgSpeedText = converter.convertFloatToString(event.getAverageSpeed(), 1);
        }
        avgSpeed.setText(avgSpeedText);

        TextView distance = (TextView)getActivity().findViewById(R.id.distance_text);
        distance.setText(converter.convertFloatToString(event.getDistance(),1));

        TextView time = (TextView)getActivity().findViewById(R.id.time_text);
        String timeText = DateUtils.formatElapsedTime(event.getElapsedTimeSeconds());
        time.setText(timeText);

        TextView hr = (TextView)getActivity().findViewById(R.id.hr_text);
        if (event.getHeartRate() > 0 && event.getHeartRate() < 255) {
            hr.setText(String.valueOf(event.getHeartRate()));
        } else {
            hr.setText("-");
        }

        TextView power = (TextView)getActivity().findViewById(R.id.power_text);
        if (event.getPower() >= 0) {
            power.setText(String.valueOf(event.getPower()));
        } else {
            power.setText("-");
        }

        TextView cadence = (TextView)getActivity().findViewById(R.id.cadence_text);
        if (event.getCyclingCadence() > 0 && event.getCyclingCadence() < 255) {
            cadence.setText(String.valueOf(event.getCyclingCadence()));
        } else {
            cadence.setText("-");
        }

        //Log.d(TAG, "updateFragment time:" + event.getElapsedTimeSeconds());
    }

    public SpeedFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_speed, container, false);

		_restoreInstanceState = false;
		if (savedInstanceState != null) {
            _restoreInstanceState = true;
        }

        return view;
    }

    @Override
    public void onResume() {
        //Log.d(TAG, "onResume");
        super.onResume();

        _prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (Constants.PREF_PEBBLE_HRM.equals(key)) {
                    applyVisibility();
                }
            }
        };
        _sharedPreferences.registerOnSharedPreferenceChangeListener(_prefListener);

        restoreFromPreferences();
    }

    @Override
    public void onPause() {
        //Log.d(TAG, "onPause");
        if (_prefListener != null) {
            _sharedPreferences.unregisterOnSharedPreferenceChangeListener(_prefListener);
            _prefListener = null;
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        //Log.d(TAG, "onSaveInstanceState");

        SharedPreferences.Editor editor = _sharedPreferences.edit();

        TextView speedText = (TextView)getActivity().findViewById(R.id.speed_text);
        editor.putString("SPEEDFRAGMENT_SPEED",speedText.getText().toString());

        TextView avgSpeed = (TextView)getActivity().findViewById(R.id.avgspeed_text);
        editor.putString("SPEEDFRAGMENT_AVGSPEED",avgSpeed.getText().toString());

        TextView distance = (TextView)getActivity().findViewById(R.id.distance_text);
        editor.putString("SPEEDFRAGMENT_DISTANCE",distance.getText().toString());

        TextView time = (TextView)getActivity().findViewById(R.id.time_text);
        editor.putString("SPEEDFRAGMENT_TIME",time.getText().toString());

        //Log.d(TAG, "onSaveInstanceState time:" + time.getText().toString());

        editor.commit();

        super.onSaveInstanceState(outState);
    }

    private void restoreFromPreferences() {
        //Log.d(TAG, "restoreFromPreferences");
        int units = _dataStore.getMeasurementUnits();

        TextView speedText = (TextView)getActivity().findViewById(R.id.speed_text);
        if (_restoreInstanceState) {
            speedText.setText(_sharedPreferences.getString("SPEEDFRAGMENT_SPEED", getString(R.string.speedfragment_speed_value)));
        } else {
            // start of the app, force instant speed to 0
            speedText.setText(getString(R.string.speedfragment_speed_value));
        }

        TextView speedTextUnits = (TextView)getActivity().findViewById(R.id.speed_units_label);
        speedTextUnits.setText(Units.getSpeedUnits(units).toUpperCase());

        TextView avgSpeed = (TextView)getActivity().findViewById(R.id.avgspeed_text);
        avgSpeed.setText(_sharedPreferences.getString("SPEEDFRAGMENT_AVGSPEED", getString(R.string.speedfragment_avgspeed_value)));

        TextView avgSpeedTextUnits = (TextView)getActivity().findViewById(R.id.avgspeed_units_label);
        avgSpeedTextUnits.setText(Units.getSpeedUnits(units).toUpperCase());

        TextView distance = (TextView)getActivity().findViewById(R.id.distance_text);
        distance.setText(_sharedPreferences.getString("SPEEDFRAGMENT_DISTANCE", getString(R.string.speedfragment_distance_value)));

        TextView distanceTextUnits = (TextView)getActivity().findViewById(R.id.distance_units_label);
        distanceTextUnits.setText(Units.getDistanceUnits(units).toUpperCase());

        TextView time = (TextView)getActivity().findViewById(R.id.time_text);
        time.setText(_sharedPreferences.getString("SPEEDFRAGMENT_TIME", getString(R.string.speedfragment_time_value)));
        //Log.d(TAG, "restoreFromPreferences time:" + _sharedPreferences.getString("SPEEDFRAGMENT_TIME", getString(R.string.speedfragment_time_value)));
        //Log.d(TAG, "_dataStore.getElapsedTime():" + _dataStore.getElapsedTime());

        TextView hr = (TextView)getActivity().findViewById(R.id.hr_text);
        hr.setText(getString(R.string.speedfragment_hr_value));

        TextView power = (TextView)getActivity().findViewById(R.id.power_text);
        power.setText(getString(R.string.speedfragment_power_value));

        TextView cadence = (TextView)getActivity().findViewById(R.id.cadence_text);
        cadence.setText(getString(R.string.speedfragment_cadence_value));

        applyVisibility();
    }
}
