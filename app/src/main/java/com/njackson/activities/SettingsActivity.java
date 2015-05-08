package com.njackson.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

import com.njackson.Constants;
import com.njackson.R;
import com.njackson.activities.HRMScanActivity;
import com.njackson.application.PebbleBikeApplication;
import com.njackson.events.GPSServiceCommand.ResetGPSState;
import com.njackson.state.IGPSDataStore;
import com.njackson.utils.services.IServiceStarter;
import com.njackson.utils.watchface.IInstallWatchFace;
import com.njackson.utils.messages.ToastMessageMaker;
import com.squareup.otto.Bus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.inject.Inject;

import de.cketti.library.changelog.ChangeLog;
import fr.jayps.android.AdvancedLocation;

import static android.support.v4.content.FileProvider.getUriForFile;

/**
 * Created by server on 28/06/2014.
 */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "PB-SettingsActivity";

    @Inject IInstallWatchFace _installWatchFace;
    @Inject SharedPreferences _sharedPreferences;
    @Inject IGPSDataStore _dataStore;
    @Inject IServiceStarter _serviceStarter;
    @Inject Bus _bus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((PebbleBikeApplication)getApplication()).inject(this);

        addPreferencesFromResource(R.xml.preferences);

        Preference installPreference = findPreference("INSTALL_WATCHFACE");
        installPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                _installWatchFace.execute(getApplicationContext(), new ToastMessageMaker());
                return true;
            }
        });

        Preference resetPreference = findPreference("RESET_DATA");
        resetPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(preference.getContext())
                        .setTitle(R.string.ALERT_RESET_DATA_TITLE)
                        .setMessage(R.string.ALERT_RESET_DATA_MESSAGE)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                _dataStore.resetAllValues();
                                _dataStore.commit();
                                _bus.post(new ResetGPSState());
                                Toast.makeText(getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
                return true;
            }
        });
        Preference exportGPXPreference = findPreference("EXPORT_GPX");
        exportGPXPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Toast.makeText(getApplicationContext(), "Please wait while generating the file", Toast.LENGTH_LONG).show();
                new Thread(new Runnable() {
                    public void run() {
                        AdvancedLocation advancedLocation = new AdvancedLocation(getApplicationContext());
                        String gpx = advancedLocation.getGPX(_sharedPreferences.getBoolean("PREF_DEBUG", false));

                        try {
                            File newFile = new File(getCacheDir(), "track.gpx");
                            FileWriter fileWriter = new FileWriter(newFile);
                            fileWriter.write(gpx);
                            fileWriter.close();
                            Uri contentUri = getUriForFile(getApplicationContext(), "com.njackson.fileprovider", newFile);

                            //Log.d(TAG, contentUri.toString());

                            final Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_STREAM,contentUri);
                            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            sendIntent.setType("application/gpx+xml");
                            //sendIntent.putExtra(Intent.EXTRA_SUBJECT, "[Subject]");
                            sendIntent.putExtra(Intent.EXTRA_TEXT, "GPS track generated by http://www.pebblebike.com");

                            startActivity(sendIntent);
                        } catch (IOException e) {
                            Log.d(TAG, "Error while creating file");
                        }
                    }
                }).start();
                return true;
            }
        });
        Preference resetGPXPreference = findPreference("RESET_TRACKS");
        resetGPXPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(preference.getContext())
                        .setTitle(R.string.ALERT_RESET_TRACKS_TITLE)
                        .setMessage(R.string.ALERT_RESET_TRACKS_MESSAGE)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                AdvancedLocation advancedLocation = new AdvancedLocation(getApplicationContext());
                                advancedLocation.resetGPX();
                                Toast.makeText(getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
                return true;
            }
        });

        final ChangeLog cl = new ChangeLog(this);
        Preference changelog = findPreference("CHANGE_LOG");
        changelog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                cl.getFullLogDialog().show();
                return true;
            }
        });

        Preference pref = findPreference("PREF_PRESSURE_INFO");
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null){
            pref.setSummary("Pressure sensor available");
        } else {
            pref.setSummary("No pressure sensor");
        }

        pref = findPreference("PREF_GEOID_HEIGHT_INFO");
        if (_sharedPreferences.getFloat("GEOID_HEIGHT", 0) != 0) {
            pref.setSummary("Correction: " + _sharedPreferences.getFloat("GEOID_HEIGHT", 0) + "m");
        } else {
            pref.setSummary("No correction");
        }

        setHrmSummary();

        // check to determine whether BLE is supported on the device.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Preference pref_hrm = findPreference("PREF_HRM");
            pref_hrm.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (preference.getKey().equals("PREF_HRM")) {
                        final Intent intent = new Intent(getApplicationContext(), HRMScanActivity.class);
                        startActivityForResult(intent, 1);
                    }
                    return false;
                }
            });
        }
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            String hrm_name = "";
            String hrm_address = "";
            if(resultCode == RESULT_OK) {
                hrm_name = data.getStringExtra("hrm_name");
                hrm_address = data.getStringExtra("hrm_address");
            }

            SharedPreferences.Editor editor = _sharedPreferences.edit();
            editor.putString("hrm_name", hrm_name);
            editor.putString("hrm_address", hrm_address);
            editor.commit();

            setHrmSummary();

            if (!hrm_address.equals("")) {
                if (_serviceStarter.isLocationServicesRunning()) {
                    Toast.makeText(getApplicationContext(), "Please restart GPS to display heart rate", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

	@Override
    protected void onResume() {
        super.onResume();

        _sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        setUnitsSummary();
        setRefreshSummary();
        setLoginJaypsSummary();
        setLoginMmtSummary();
        setLiveSummary();
        setOruxMapsSummary();
        setCanvasSummary();
    }

    @Override
    protected void onPause() {
        _sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Log.i(TAG, "onSharedPreferenceChanged" + s);
        _dataStore.reloadPreferencesFromSettings();
        if (s.equals("UNITS_OF_MEASURE")) {
            setUnitsSummary();
        }
        if (s.equals("REFRESH_INTERVAL")) {
            setRefreshSummary();
        }
        if (s.equals("LIVE_TRACKING") || s.equals("LIVE_TRACKING_MMT")) {
            setLiveSummary();
        }
        if (s.equals("LIVE_TRACKING_LOGIN")) {
            setLiveSummary();
            setLoginJaypsSummary();
        }
        if (s.equals("LIVE_TRACKING_MMT_LOGIN")) {
            setLiveSummary();
            setLoginMmtSummary();
        }
        if (s.equals("LIVE_TRACKING_PASSWORD") || s.equals("LIVE_TRACKING_MMT_PASSWORD")) {
            setLiveSummary();
        }
        if (s.equals("ORUXMAPS_AUTO")) {
            setOruxMapsSummary();
        }
        if (s.equals("CANVAS_MODE")) {
            setCanvasSummary();
        }
    }

    private void setUnitsSummary() {
        String units = _sharedPreferences.getString("UNITS_OF_MEASURE", "0");
        Preference unitsPref = findPreference("UNITS_OF_MEASURE");

        if (units.equals(""+Constants.IMPERIAL)) {
            unitsPref.setSummary(getString(R.string.PREF_UNITS_UNIT_IMPERIAL));
        } else if (units.equals(""+Constants.METRIC)) {
            unitsPref.setSummary(getString(R.string.PREF_UNITS_UNIT_METRIC));
        } else if (units.equals(""+Constants.NAUTICAL_IMPERIAL)) {
            unitsPref.setSummary(getString(R.string.PREF_UNITS_UNIT_NAUTICAL_IMPERIAL));
        } else if (units.equals(""+Constants.NAUTICAL_METRIC)) {
            unitsPref.setSummary(getString(R.string.PREF_UNITS_UNIT_NAUTICAL_METRIC));
        } else if (units.equals(""+Constants.RUNNING_IMPERIAL)) {
            unitsPref.setSummary(getString(R.string.PREF_UNITS_UNIT_RUNNING_IMPERIAL));
        } else if (units.equals(""+Constants.RUNNING_METRIC)) {
            unitsPref.setSummary(getString(R.string.PREF_UNITS_UNIT_RUNNING_METRIC));
        }
    }

    private void setRefreshSummary() {
        int refresh_interval = 0;

        try {
            refresh_interval = Integer.valueOf(_sharedPreferences.getString("REFRESH_INTERVAL", "500"));
        }catch (NumberFormatException nfe) {
            refresh_interval = Constants.REFRESH_INTERVAL_DEFAULT;
        }

        Preference refreshPref = findPreference("REFRESH_INTERVAL");

        if (refresh_interval < 1000) {
            refreshPref.setSummary(refresh_interval + " ms");
        } else {
            refreshPref.setSummary(refresh_interval/1000 + " s");
        }
    }

    private void setLoginJaypsSummary() {
        String login = _sharedPreferences.getString("LIVE_TRACKING_LOGIN", "");
        Preference loginPref = findPreference("LIVE_TRACKING_LOGIN");
        loginPref.setSummary(login);
    }

    private void setLoginMmtSummary() {
        String login = _sharedPreferences.getString("LIVE_TRACKING_MMT_LOGIN", "");
        Preference loginPref = findPreference("LIVE_TRACKING_MMT_LOGIN");
        loginPref.setSummary(login);
    }

    // TODO(jay) : call me when PreferenceScreen "live_screen" is closed
    private void setLiveSummary() {
        Boolean live_jayps = _sharedPreferences.getBoolean("LIVE_TRACKING", false) && !_sharedPreferences.getString("LIVE_TRACKING_LOGIN", "").equals("") && !_sharedPreferences.getString("LIVE_TRACKING_PASSWORD", "").equals("");
        Boolean live_mmt = _sharedPreferences.getBoolean("LIVE_TRACKING_MMT", false) && !_sharedPreferences.getString("LIVE_TRACKING_MMT_LOGIN", "").equals("") && !_sharedPreferences.getString("LIVE_TRACKING_MMT_PASSWORD", "").equals("");
        Preference live_screen = findPreference("live_screen");
        String live = "Disable";
        if (live_jayps && live_mmt) {
            live = "JayPS & Map My Tracks";
        } else if (live_jayps) {
            live = "JayPS";
        } else if (live_mmt) {
            live = "Map My Track";
        }
        live_screen.setSummary(live);
    }

    private void setOruxMapsSummary() {
        ListPreference oruxPref = (ListPreference) findPreference("ORUXMAPS_AUTO");
        CharSequence listDesc = oruxPref.getEntry();
        oruxPref.setSummary(listDesc);
    }

    private void setCanvasSummary() {
        ListPreference canvasPref = (ListPreference) findPreference("CANVAS_MODE");
        CharSequence listDesc = canvasPref.getEntry();
        canvasPref.setSummary(listDesc);
    }

    private void setHrmSummary() {
        String summary = _sharedPreferences.getString("hrm_name", "");
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            summary = getResources().getString(R.string.ble_not_supported);
        }
        if (summary.equals("")) {
            summary = "Click to choose a sensor";
        }
        Preference loginPref = findPreference("PREF_HRM");
        loginPref.setSummary(summary);
    }
}
