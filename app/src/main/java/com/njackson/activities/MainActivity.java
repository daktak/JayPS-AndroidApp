package com.njackson.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.njackson.Constants;
import com.njackson.R;
import com.njackson.analytics.IAnalytics;
import com.njackson.application.PebbleBikeApplication;
import com.njackson.changelog.IChangeLog;
import com.njackson.changelog.IChangeLogBuilder;
import com.njackson.events.ActivityRecognitionCommand.ActivityRecognitionStatus;
import com.njackson.events.GPSServiceCommand.GPSStatus;
import com.njackson.events.GPSServiceCommand.ResetGPSState;
import com.njackson.events.UI.StartButtonTouchedEvent;
import com.njackson.events.UI.StopButtonTouchedEvent;
import com.njackson.events.base.BaseStatus;
import com.njackson.gps.Navigator;
import com.njackson.state.IGPSDataStore;
import com.njackson.upload.RunkeeperUpload;
import com.njackson.upload.StravaUpload;
import com.njackson.utils.googleplay.IGooglePlayServices;
import com.njackson.utils.gpx.GpxExport;
import com.njackson.utils.services.IServiceStarter;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

public class MainActivity extends FragmentActivity  implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "PB-MainActivity";
    @Inject Bus _bus;
    @Inject IAnalytics _analytics;
    @Inject IServiceStarter _serviceStarter;
    @Inject IGooglePlayServices _playServices;
    @Inject SharedPreferences _sharedPreferences;
    @Inject IChangeLogBuilder _changeLogBuilder;
    @Inject IGPSDataStore _dataStore;
    @Inject Navigator _navigator;
    @Inject IAnalytics _parseAnalytics;

    @Subscribe
    public void onStartButtonTouched(StartButtonTouchedEvent event) {
        _serviceStarter.startLocationServices();
    }

    @Subscribe
    public void onStopButtonTouched(StopButtonTouchedEvent event) {
        _serviceStarter.stopLocationServices();
    }

    @Subscribe
    public void onRecognitionState(ActivityRecognitionStatus event) {
        if(event.getStatus() == ActivityRecognitionStatus.Status.UNABLE_TO_START) {
            Toast.makeText(this, R.string.alert_google_play_not_available, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "PLAY_NOT_AVAILABLE");
        }
    }

    @Subscribe
    public void onGPSServiceState(GPSStatus event) {
        if (event.getStatus() == BaseStatus.Status.DISABLED) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage(R.string.alert_gps_off_enable_it)
                    .setCancelable(false)
                    .setPositiveButton(R.string.alert_gps_go_to_settings,
                            new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int id){
                                    Intent callGPSSettingIntent = new Intent(
                                            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivity(callGPSSettingIntent);
                                }
                            });
            alertDialogBuilder.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int id){
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = alertDialogBuilder.create();
            alert.show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((PebbleBikeApplication) getApplication()).inject(this);

        _analytics.trackAppOpened(getIntent());
        boolean activity_start = _sharedPreferences.getBoolean("ACTIVITY_RECOGNITION",false);
        if(activity_start) {
            _serviceStarter.startActivityService();
        }

        detectNewVersion();
        showChangeLog();

        if (getIntent().getExtras() != null) {
            onNewIntent(getIntent());
        }
    }

    private void showChangeLog() {
        IChangeLog changeLog = _changeLogBuilder.setActivity(this).build();
        if (changeLog.isFirstRun()) {
            changeLog.getDialog().show();
        }
    }
    private void detectNewVersion() {

        // Get last version code
        int mLastVersionCode = _sharedPreferences.getInt("VERSION_CODE", 0);
        int mCurrentVersionCode = 0;

        // Get current version code
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            mCurrentVersionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            mCurrentVersionCode = 0;
        }

        if (mLastVersionCode < mCurrentVersionCode) {
            Log.d(TAG, "newVersion: " + mLastVersionCode + " -> " + mCurrentVersionCode);

            SharedPreferences.Editor editor = _sharedPreferences.edit();

            if (mLastVersionCode == 0) {
                // first run or migration from v1
                // try to import saved data from v1
                SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME_V1, 0);
                _dataStore.setStartTime(settings.getLong("GPS_LAST_START", 0));
                _dataStore.setDistance(settings.getFloat("GPS_DISTANCE", 0));
                _dataStore.setElapsedTime(settings.getLong("GPS_ELAPSEDTIME", 0));
                _dataStore.setAscent(settings.getFloat("GPS_ASCENT", 0));
                _dataStore.commit();

                editor.putString("hrm_name", settings.getString("hrm_name", ""));
                editor.putString("hrm_address", settings.getString("hrm_address", ""));
            }
            // save new version code
            editor.putInt("VERSION_CODE", mCurrentVersionCode);
            editor.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        _bus.register(this);
        _sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        _bus.unregister(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        _sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        }
        if (id == R.id.action_export_gpx) {
            if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) {
                GpxExport.export(getApplicationContext(), _sharedPreferences.getBoolean("ADVANCED_GPX", false), _sharedPreferences.getString("EXPORT_EMAIL", ""));
                _parseAnalytics.trackEvent("gpx_export");
            } else {
                Toast.makeText(getApplicationContext(), R.string.alert_tracks_gpx_export, Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_upload_strava) {
            if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) {
                if (!_sharedPreferences.getString("strava_token", "").isEmpty()) {
                    StravaUpload strava_upload = new StravaUpload(this);
                    strava_upload.upload(_sharedPreferences.getString("strava_token", ""));
                } else {
                    Toast.makeText(getApplicationContext(), R.string.alert_configure_strava_upload, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.alert_enable_tracks_strava_upload, Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_upload_runkeeper) {
            if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) {
                if (!_sharedPreferences.getString("runkeeper_token", "").isEmpty()) {
                    RunkeeperUpload runkeeper_upload = new RunkeeperUpload(this);
                    runkeeper_upload.upload(_sharedPreferences.getString("runkeeper_token", ""));
                } else {
                    Toast.makeText(getApplicationContext(), R.string.alert_configure_runkeeper_upload, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.alert_enable_tracks_runkeeper_upload, Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_load_route) {
            Toast.makeText(getApplicationContext(), R.string.alert_open_gpx_file, Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            //intent.setType("application/gpx+xml"); // does not work for all gpx file.... (ko: recent, dropbox...)
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(intent, getString(R.string.alert_select_txt_file)), Constants.CODE_LOAD_GPX);
            } catch (android.content.ActivityNotFoundException ex) {
                // Potentially direct the user to the Market with a Dialog
                Toast.makeText(getApplicationContext(), R.string.alert_unable_to_open_file, Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_reset) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.ALERT_RESET_DATA_TITLE)
                    .setMessage(R.string.ALERT_RESET_DATA_MESSAGE)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            _dataStore.resetAllValues();
                            _dataStore.commit();
                            _bus.post(new ResetGPSState());
                            AdvancedLocation advancedLocation = new AdvancedLocation(getApplicationContext());
                            advancedLocation.resetGPX();
                            Toast.makeText(getApplicationContext(), R.string.done, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();
            return true;
        }
        if (id == R.id.action_share_location) {
            float lat = _dataStore.getLastLocationLatitude();
            float lon = _dataStore.getLastLocationLongitude();
            if ((lat!=0.0)&&(lon!=0.0)) {
                String uri = "geo:" + lat + ","+ lon + "?q=" + lat + "," + lon;
                startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.compareTo("ACTIVITY_RECOGNITION") == 0) {
            boolean activity_start = sharedPreferences.getBoolean("ACTIVITY_RECOGNITION",false);
            if(activity_start) {
                _serviceStarter.startActivityService();
            } else {
                _serviceStarter.stopActivityService();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Log.d(TAG, "requestCode=" + requestCode + " resultCode=" + resultCode);
        if (requestCode == Constants.CODE_LOAD_GPX) {
            if (data != null) {
                try {
                    Uri uri = data.getData();
                    BufferedReader br = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
                    StringBuilder gpx = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        gpx.append(line).append('\n');
                    }
                    _navigator.loadGpx(gpx.toString());
                    _parseAnalytics.trackEvent("navigation_load");
                    Toast.makeText(getApplicationContext(), R.string.alert_route_loaded + " - " + _navigator.getNbPoints() + " points", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Exception:" + e);
                }
            }
        }
    }
}
