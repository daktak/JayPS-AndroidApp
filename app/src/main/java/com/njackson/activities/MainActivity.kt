package com.njackson.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.njackson.Constants
import com.njackson.R
import com.njackson.application.PebbleBikeApplication
import com.njackson.changelog.IChangeLogBuilder
import com.njackson.events.ActivityRecognitionCommand.ActivityRecognitionStatus
import com.njackson.events.GPSServiceCommand.GPSStatus
import com.njackson.events.GPSServiceCommand.ResetGPSState
import com.njackson.events.base.BaseStatus
import com.njackson.gps.Navigator
import com.njackson.state.IGPSDataStore
import com.njackson.ui.dashboard.DashboardScreen
import com.njackson.ui.dashboard.DashboardViewModel
import com.njackson.ui.settings.SettingsNavHost
import com.njackson.ui.settings.SettingsViewModel
import com.njackson.ui.theme.KaypsTheme
import com.njackson.upload.StravaUpload
import com.njackson.utils.UpdateTask
import com.njackson.utils.gpx.GpxExport
import com.njackson.utils.services.IServiceStarter
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import fr.jayps.android.AdvancedLocation
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class MainActivity : FragmentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject lateinit var _bus: Bus
    @Inject lateinit var _serviceStarter: IServiceStarter
    @Inject lateinit var _sharedPreferences: SharedPreferences
    @Inject lateinit var _changeLogBuilder: IChangeLogBuilder
    @Inject lateinit var _dataStore: IGPSDataStore
    @Inject lateinit var _navigator: Navigator

    private lateinit var dashVm: DashboardViewModel
    private lateinit var settingsVm: SettingsViewModel
    private var pendingBle = -1

    companion object {
        private const val TAG = "PB-MainActivity"
        private const val REQ_PERMS = 100
        private const val REQ_START_LOC = 101
    }

    private val gpxLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data ?: return@registerForActivityResult
        try {
            val uri = data.data ?: return@registerForActivityResult
            val br = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
            val gpx = StringBuilder(); var line: String?
            while (br.readLine().also { line = it } != null) gpx.append(line).append('\n')
            _navigator.loadGpx(gpx.toString())
            Toast.makeText(applicationContext, "Route loaded - ${_navigator.getNbPoints()} points", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Log.e(TAG, "Exception:$e") }
    }
    private val bleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (pendingBle < 0) return@registerForActivityResult
        var name = ""; var addr = ""
        if (res.resultCode == RESULT_OK && res.data != null) {
            name = res.data!!.getStringExtra("hrm_name") ?: ""
            addr = res.data!!.getStringExtra("hrm_address") ?: ""
        }
        settingsVm.setBle(pendingBle, name, addr)
        if (addr.isNotEmpty() && _serviceStarter.isLocationServicesRunning()) Toast.makeText(applicationContext, "Please restart GPS to display BLE sensor data", Toast.LENGTH_LONG).show()
        pendingBle = -1
    }

    @Subscribe fun onRecognitionState(e: ActivityRecognitionStatus) {
        if (e.getStatus() == BaseStatus.Status.UNABLE_TO_START) {
            Toast.makeText(this, R.string.alert_google_play_not_available, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "PLAY_NOT_AVAILABLE")
        }
    }
    @Subscribe fun onGPSServiceState(e: GPSStatus) {
        if (e.getStatus() == BaseStatus.Status.DISABLED) {
            AlertDialog.Builder(this).setMessage(R.string.alert_gps_off_enable_it).setCancelable(false)
                .setPositiveButton(R.string.alert_gps_go_to_settings) { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("Cancel") { d, _ -> d.cancel() }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        try {
            val cfg = org.osmdroid.config.Configuration.getInstance()
            cfg.userAgentValue = packageName
            cfg.osmdroidBasePath = getDir("osmdroid", MODE_PRIVATE)
            cfg.osmdroidTileCache = getDir("osmdroid/tiles", MODE_PRIVATE)
        } catch (_: Exception) {}
        (application as PebbleBikeApplication).inject(this)
        dashVm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(c: Class<T>): T = DashboardViewModel(_bus, _dataStore, _sharedPreferences) as T
        })[DashboardViewModel::class.java]
        settingsVm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(c: Class<T>): T = SettingsViewModel(_sharedPreferences, _dataStore, _bus, applicationContext) as T
        })[SettingsViewModel::class.java]

        requestRequiredPermissions()
        if (_sharedPreferences.getBoolean("ACTIVITY_RECOGNITION", false)) _serviceStarter.startActivityService()
        detectNewVersion(); showChangeLog(); UpdateTask(this, false).update()
        if (intent.extras != null) onNewIntent(intent)

        setContent {
            KaypsTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "dashboard") {
                    composable("dashboard") {
                        val state by dashVm.state.collectAsState()
                        DashboardScreen(state = state, onStartStop = { handleStartStop() }, onMenu = { id -> handleMenu(id, nav) })
                    }
                    composable("settings") {
                        SettingsNavHost(rootNav = nav, vm = settingsVm,
                            onPickGpx = { gpxLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }.let { Intent.createChooser(it, getString(R.string.alert_select_txt_file)) }) },
                            onScanBle = { idx -> pendingBle = idx; bleLauncher.launch(Intent(applicationContext, HRMScanActivity::class.java)) },
                            onExport = { type -> GpxExport.export(applicationContext, _sharedPreferences.getBoolean("ADVANCED_GPX", false), _sharedPreferences.getString("EXPORT_EMAIL", "") ?: "", type, if (type=="tcx") _sharedPreferences.getString("TCX_ACTIVITY_TYPE", "Biking") ?: "Biking" else "") },
                            onResetData = { _dataStore.resetAllValues(); _dataStore.commit(); _bus.post(ResetGPSState()); AdvancedLocation(applicationContext).resetGPX(); Toast.makeText(applicationContext, "Done", Toast.LENGTH_SHORT).show() },
                            onResetTracks = { AdvancedLocation(applicationContext).resetGPX(); Toast.makeText(applicationContext, "Done", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
        }
    }

    private fun handleStartStop() {
        val s = dashVm.state.value
        if (s.isRunning) { _serviceStarter.stopLocationServices() }
        else {
            if (_sharedPreferences.getBoolean(Constants.PREF_INDOOR_MODE, false)) { _serviceStarter.startLocationServices(); return }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                _serviceStarter.startLocationServices()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQ_START_LOC)
            }
        }
    }

    private fun handleMenu(id: String, nav: androidx.navigation.NavController) {
        when (id) {
            "action_settings" -> nav.navigate("settings")
            "action_export_gpx" -> if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) GpxExport.export(applicationContext, _sharedPreferences.getBoolean("ADVANCED_GPX", false), _sharedPreferences.getString("EXPORT_EMAIL", "")!!, "gpx", "") else Toast.makeText(applicationContext, R.string.alert_tracks_gpx_export, Toast.LENGTH_SHORT).show()
            "action_export_tcx" -> if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) GpxExport.export(applicationContext, _sharedPreferences.getBoolean("ADVANCED_GPX", false), _sharedPreferences.getString("EXPORT_EMAIL", "")!!, "tcx", _sharedPreferences.getString("TCX_ACTIVITY_TYPE", "Biking")!!) else Toast.makeText(applicationContext, R.string.alert_tracks_gpx_export, Toast.LENGTH_SHORT).show()
            "action_load_route" -> {
                Toast.makeText(applicationContext, R.string.alert_open_gpx_file, Toast.LENGTH_SHORT).show()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                try { gpxLauncher.launch(Intent.createChooser(intent, getString(R.string.alert_select_txt_file))) } catch (_: Exception) { Toast.makeText(applicationContext, R.string.alert_unable_to_open_file, Toast.LENGTH_SHORT).show() }
            }
            "action_reset" -> AlertDialog.Builder(this).setTitle(R.string.ALERT_RESET_DATA_TITLE).setMessage(R.string.ALERT_RESET_DATA_MESSAGE).setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ -> _dataStore.resetAllValues(); _dataStore.commit(); _bus.post(ResetGPSState()); AdvancedLocation(applicationContext).resetGPX(); Toast.makeText(applicationContext, "Done", Toast.LENGTH_SHORT).show() }
                .setNegativeButton(android.R.string.no, null).show()
            "action_share_location" -> {
                val lat = _dataStore.getLastLocationLatitude(); val lon = _dataStore.getLastLocationLongitude()
                if (lat != 0f && lon != 0f) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
            }
            "action_upload_strava" -> if (_sharedPreferences.getBoolean("ENABLE_TRACKS", false)) {
                val session = _sharedPreferences.getString("STRAVA_SESSION", "")
                if (!session.isNullOrEmpty()) StravaUpload(applicationContext).upload(session) else Toast.makeText(applicationContext, "Please set the Strava session cookie in the settings", Toast.LENGTH_LONG).show()
            } else Toast.makeText(applicationContext, "Please enable tracks in the settings to save GPX before uploading to Strava", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangeLog() {
        val cl = _changeLogBuilder.setActivity(this).build()
        if (cl.isFirstRun) cl.dialog.show()
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        if (!_sharedPreferences.getBoolean(Constants.PREF_INDOOR_MODE, false) && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(c: Int, perms: Array<String>, res: IntArray) {
        super.onRequestPermissionsResult(c, perms, res)
        if (c == REQ_START_LOC) {
            val granted = perms.indices.any { perms[it] == android.Manifest.permission.ACCESS_FINE_LOCATION && res[it] == PackageManager.PERMISSION_GRANTED }
            if (granted && !_sharedPreferences.getBoolean(Constants.PREF_INDOOR_MODE, false)) _serviceStarter.startLocationServices()
        }
    }

    private fun detectNewVersion() {
        var last = _sharedPreferences.getInt("VERSION_CODE", 0)
        var cur = 0
        try { cur = packageManager.getPackageInfo(packageName, 0).versionCode } catch (_: Exception) { cur = 0 }
        if (last < cur) {
            Log.d(TAG, "newVersion: $last -> $cur")
            val e = _sharedPreferences.edit()
            if (last == 0) {
                val s = getSharedPreferences(Constants.PREFS_NAME_V1, 0)
                _dataStore.setStartTime(s.getLong("GPS_LAST_START", 0))
                _dataStore.setDistance(s.getFloat("GPS_DISTANCE", 0f))
                _dataStore.setElapsedTime(s.getLong("GPS_ELAPSEDTIME", 0))
                _dataStore.setAscent(s.getFloat("GPS_ASCENT", 0f))
                _dataStore.commit()
                e.putString("hrm_name1", s.getString("hrm_name", ""))
                e.putString("hrm_address1", s.getString("hrm_address", ""))
            }
            e.putInt("VERSION_CODE", cur).commit()
        }
    }

    override fun onResume() { super.onResume(); _bus.register(this); _sharedPreferences.registerOnSharedPreferenceChangeListener(this); _serviceStarter.broadcastLocationState() }
    override fun onPause() { super.onPause(); try { _bus.unregister(this) } catch (_: Exception) {} }
    override fun onDestroy() { super.onDestroy(); _sharedPreferences.unregisterOnSharedPreferenceChangeListener(this) }
    override fun onSharedPreferenceChanged(p: SharedPreferences, k: String?) {
        if (k == "ACTIVITY_RECOGNITION") { if (p.getBoolean("ACTIVITY_RECOGNITION", false)) _serviceStarter.startActivityService() else _serviceStarter.stopActivityService() }
    }
}
