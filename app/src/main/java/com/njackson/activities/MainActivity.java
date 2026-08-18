package com.njackson.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.njackson.Constants;
import com.njackson.R;
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
import com.njackson.upload.StravaUpload;
import com.njackson.utils.gpx.GpxExport;
import com.njackson.utils.services.IServiceStarter;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

public class MainActivity extends FragmentActivity  implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "PB-MainActivity";
    @Inject Bus _bus;
    @Inject IServiceStarter _serviceStarter;
    @Inject SharedPreferences _sharedPreferences;
    @Inject IChangeLogBuilder _changeLogBuilder;
    @Inject IGPSDataStore _dataStore;
    @Inject Navigator _navigator;

    private boolean _authInProgress;

    private static final int REQUEST_REQUIRED_PERMISSIONS = 100;

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
            Toast.makeText(this, "Google Play Services is not available", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "PLAY_NOT_AVAILABLE");
        }
    }

    @Subscribe
    public void onGPSServiceState(GPSStatus event) {
        if (event.getStatus() == BaseStatus.Status.DISABLED) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage("GPS is disabled in your device. Would you like to enable it?")
                    .setCancelable(false)
                    .setPositiveButton("Goto Settings Page To Enable GPS",
                            new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int id){
                                    Intent callGPSSettingIntent = new Intent(
                                            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivity(callGPSSettingIntent);
                                }
                            });
            alertDialogBuilder.setNegativeButton("Cancel",
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

        requestRequiredPermissions();

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

    private void requestRequiredPermissions() {
        ArrayList<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(android.Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_REQUIRED_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_REQUIRED_PERMISSIONS) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "permission denied: " + permissions[i]);
                }
            }
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
                _dataStore.setAscent((float) settings.getFloat("GPS_ASCENT", 0));
                _dataStore.commit();

                editor.putString("hrm_name1", settings.getString("hrm_name", ""));
                editor.putString("hrm_address1", settings.getString("hrm_address", ""));
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
                GpxExport.export(getApplicationContext(), _sharedPreferences.getBoolean("ADVANCED_GPX", false), "gpx", "");
            } else {
                Toast.makeText(getApplicationContext(), "Please enable tracks in the settings to save GPX before using the export", Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_export_tcx) {
            if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) {
                GpxExport.export(getApplicationContext(), _sharedPreferences.getBoolean("ADVANCED_GPX", false), "tcx", _sharedPreferences.getString("TCX_ACTIVITY_TYPE", "Biking"));
            } else {
                Toast.makeText(getApplicationContext(), "Please enable tracks in the settings to save GPX before using the export", Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_load_route) {
            Toast.makeText(getApplicationContext(), "Open a GPX file", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            //intent.setType("application/gpx+xml"); // does not work for all gpx file.... (ko: recent, dropbox...)
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(intent, "Select txt file"), Constants.CODE_LOAD_GPX);
            } catch (android.content.ActivityNotFoundException ex) {
                // Potentially direct the user to the Market with a Dialog
                Toast.makeText(getApplicationContext(), "Impossible to open file", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
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
        if (id == R.id.action_upload_strava) {
            if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) {
                String session = _sharedPreferences.getString("STRAVA_SESSION", "");
                if (!session.isEmpty()) {
                    new StravaUpload(getApplicationContext()).upload(session);
                } else {
                    Toast.makeText(getApplicationContext(), "Please set the Strava session cookie in the settings", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Please enable tracks in the settings to save GPX before uploading to Strava", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getApplicationContext(), "Route loaded - " + _navigator.getNbPoints() + " points", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Exception:" + e);
                }
            }
        }
    }
}
