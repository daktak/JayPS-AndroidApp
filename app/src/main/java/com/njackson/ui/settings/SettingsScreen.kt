package com.njackson.ui.settings
import android.app.Activity

import android.content.Intent
import android.net.Uri
import de.cketti.library.changelog.ChangeLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.njackson.Constants
import com.njackson.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNavHost(rootNav: NavController, vm: SettingsViewModel, onPickGpx: () -> Unit, onScanBle: (Int) -> Unit, onExport: (String) -> Unit, onResetData: () -> Unit, onResetTracks: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "root") {
        composable("root") { SettingsRoot(nav, vm, onPickGpx, onScanBle, onExport, onResetData, onResetTracks) }
        composable("tracks") { TracksGroup(nav, vm) }
        composable("sensors") { SensorsGroup(nav, vm, onScanBle) }
        composable("navigation") { NavigationGroup(nav, vm, onPickGpx) }
        composable("integration") { IntegrationRoot(nav, vm) }
        composable("live_nextcloud") { NextcloudGroup(nav, vm) }
        composable("live_mmt") { MmtGroup(nav, vm) }
        composable("orux") { OruxGroup(nav, vm) }
        composable("strava") { StravaGroup(nav, vm) }
        composable("advanced") { AdvancedGroup(nav, vm) }
        composable("about") { AboutGroup(nav, vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, nav: NavController, content: @Composable () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad -> Box(modifier = Modifier.padding(pad)) { content() } }
}

@Composable
private fun SettingsRoot(nav: NavController, vm: SettingsViewModel, onPickGpx: () -> Unit, onScanBle: (Int) -> Unit, onExport: (String) -> Unit, onResetData: () -> Unit, onResetTracks: () -> Unit) {
    val ctx = LocalContext.current
    val pebbleUrl = stringResource(R.string.PREF_PEBBLE_STORE_URL)
    val watchfaceUrl = stringResource(R.string.PREF_INSTALL_WATCHFACE_URL)
    SettingsScaffold(stringResource(R.string.settings_title), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_general_title), Icons.Filled.Tune) { ClickRow(stringResource(R.string.PREF_RESET_DATA), stringResource(R.string.PREF_RESET_DATA_SUMMARY), onResetData) } }
            item { GroupCard(stringResource(R.string.settings_tracks_units_title), Icons.Filled.Storage) { ClickRow(stringResource(R.string.settings_tracks_title), stringResource(R.string.settings_tracks_units_summary)) { nav.navigate("tracks") } } }
            item { GroupCard(stringResource(R.string.settings_sensors_title), Icons.Filled.Bluetooth) { ClickRow(stringResource(R.string.settings_sensors_title), stringResource(R.string.settings_sensors_summary)) { nav.navigate("sensors") } } }
            item { GroupCard(stringResource(R.string.settings_navigation_title), Icons.Filled.Navigation) { ClickRow(stringResource(R.string.settings_navigation_title), stringResource(R.string.settings_navigation_summary)) { nav.navigate("navigation") } } }
            item { GroupCard(stringResource(R.string.settings_integration_title), Icons.Filled.Share) { ClickRow(stringResource(R.string.settings_integration_title), stringResource(R.string.settings_integration_summary)) { nav.navigate("integration") } } }
            item { GroupCard(stringResource(R.string.settings_advanced_title), Icons.Filled.Memory) { ClickRow(stringResource(R.string.settings_advanced_title), stringResource(R.string.settings_advanced_summary)) { nav.navigate("advanced") } } }
            item { GroupCard(stringResource(R.string.settings_about_title), Icons.Filled.Info) { ClickRow(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_summary)) { nav.navigate("about") } } }
            item { GroupCard(stringResource(R.string.settings_install_title), Icons.Filled.InstallMobile) {
                ClickRow(stringResource(R.string.PREF_PEBBLE_STORE), stringResource(R.string.PREF_PEBBLE_STORE_SUMMARY)) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pebbleUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }
                ClickRow(stringResource(R.string.PREF_INSTALL_WATCHFACE_WEBSITE), "") { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(watchfaceUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }
            } }
        }
    }
}

@Composable
private fun TracksGroup(nav: NavController, vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    var unitsOpen by remember { mutableStateOf(false) }
    var tcxOpen by remember { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.PREF_TRACKS_CATEGORY_TITLE), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.PREF_UNITS_TITLE), Icons.Filled.Speed) { ClickRow(stringResource(R.string.PREF_UNITS_TITLE), unitsLabel(s.units)) { unitsOpen = true } } }
            item { GroupCard(stringResource(R.string.PREF_TRACKS_CATEGORY_TITLE), Icons.Filled.Storage) {
                SwitchRow(stringResource(R.string.PREF_ENABLE_TRACKS_TITLE), stringResource(R.string.PREF_ENABLE_TRACKS_SUMMARY), s.enableTracks) { vm.putBool("ENABLE_TRACKS", it) }
                EditRow(stringResource(R.string.PREF_EXPORT_EMAIL_TITLE), s.exportEmail) { vm.putString("EXPORT_EMAIL", it) }
                ClickRow(stringResource(R.string.PREF_EXPORT_GPX_TITLE), stringResource(R.string.PREF_EXPORT_GPX_SUMMARY)) { }
                ClickRow(stringResource(R.string.PREF_EXPORT_TCX_TITLE), stringResource(R.string.PREF_EXPORT_TCX_SUMMARY)) { }
                ClickRow(stringResource(R.string.TCX_ACTIVITY_TYPE_TITLE), s.tcxActivity) { tcxOpen = true }
                ClickRow(stringResource(R.string.PREF_RESET_TRACKS_TITLE), stringResource(R.string.PREF_RESET_TRACKS_SUMMARY)) { }
                SwitchRow("Advanced GPX Export", "Add ascent, GPS and pressure altitudes", s.advancedGpx) { vm.putBool("ADVANCED_GPX", it) }
            } }
            item { GroupCard(stringResource(R.string.PREF_AUTOSTART_TITLE), Icons.Filled.DirectionsBike) {
                SwitchRow(stringResource(R.string.PREF_AUTOSTART_TITLE), stringResource(R.string.PREF_AUTOSTART_SUMMARY), s.activityRecognition) { vm.putBool("ACTIVITY_RECOGNITION", it) }
                SwitchRow(stringResource(R.string.PREF_AUTOSTART_WALKING_SUMMARY), "", s.activityWalking) { vm.putBool("ACTIVITY_RECOGNITION_WALKING", it) }
            } }
        }
        if (unitsOpen) ListDialog(stringResource(R.string.PREF_UNITS_TITLE), arrayOf(stringResource(R.string.PREF_UNITS_UNIT_IMPERIAL), stringResource(R.string.PREF_UNITS_UNIT_METRIC), stringResource(R.string.PREF_UNITS_UNIT_NAUTICAL_IMPERIAL), stringResource(R.string.PREF_UNITS_UNIT_NAUTICAL_METRIC), stringResource(R.string.PREF_UNITS_UNIT_RUNNING_IMPERIAL), stringResource(R.string.PREF_UNITS_UNIT_RUNNING_METRIC)), arrayOf("0","1","2","3","4","5"), s.units, { unitsOpen = false }, { vm.putString("UNITS_OF_MEASURE", it); unitsOpen = false })
        if (tcxOpen) ListDialog(stringResource(R.string.TCX_ACTIVITY_TYPE_TITLE), arrayOf("Biking","Running","Other"), arrayOf("Biking","Running","Other"), s.tcxActivity, { tcxOpen = false }, { vm.putString("TCX_ACTIVITY_TYPE", it); tcxOpen = false })
    }
}

@Composable
private fun SensorsGroup(nav: NavController, vm: SettingsViewModel, onScanBle: (Int) -> Unit) {
    val s by vm.state.collectAsState()
    var refreshOpen by remember { mutableStateOf(false) }
    var wheelPresetOpen by remember { mutableStateOf(false) }
    var hrmZoneOpen by remember { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.settings_sensors_title), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_gps_title), Icons.Filled.Speed) {
                SwitchRow(stringResource(R.string.INDOOR_MODE_TITLE), stringResource(R.string.INDOOR_MODE_SUMMARY), s.indoorMode) { vm.putBool(Constants.PREF_INDOOR_MODE, it) }
                ClickRow(stringResource(R.string.REFRESH_INTERVAL_TITLE), refreshLabel(s.refreshInterval)) { refreshOpen = true }
            } }
            item { GroupCard(stringResource(R.string.settings_ble_title), Icons.Filled.Bluetooth) {
                (0..5).forEach { i ->
                    val name = s.bleNames[i]
                    val summary = if (name.isEmpty()) stringResource(R.string.pref_choose_sensor) else name
                    ClickRow(stringResource(R.string.PREF_BLE_TITLE) + " ${i+1}", summary) { onScanBle(i) }
                }
                EditRow(stringResource(R.string.PREF_BLE_HRM_HRMAX), s.hrmMax) { vm.putString("PREF_BLE_HRM_HRMAX", it) }
                ClickRow(stringResource(R.string.PREF_BLE_HRM_ZONE_NOTIFICATION_MODE), hrmZoneLabel(s.hrmZone)) { hrmZoneOpen = true }
                SwitchRow(stringResource(R.string.PREF_PEBBLE_HRM_TITLE), stringResource(R.string.PREF_PEBBLE_HRM_SUMMARY), s.pebbleHrm) { vm.putBool(Constants.PREF_PEBBLE_HRM, it) }
                ClickRow(stringResource(R.string.PREF_BLE_CSC_WHEEL_PRESET_TITLE), s.wheelPreset.ifEmpty { "Custom" }) { wheelPresetOpen = true }
                EditRow(stringResource(R.string.PREF_BLE_CSC_WHEEL_SIZE), s.wheelSize) { vm.putString("PREF_BLE_CSC_WHEEL_SIZE", it) }
                SwitchRow(stringResource(R.string.autostart_lights_title), stringResource(R.string.autostart_lights_summary), s.autostartLights) { vm.putBool(Constants.PREF_AUTOSTART_LIGHTS, it) }
                SwitchRow(stringResource(R.string.autostart_gopro_title), stringResource(R.string.autostart_gopro_summary), s.autostartGoPro) { vm.putBool(Constants.PREF_AUTOSTART_GOPRO, it) }
            } }
            item { GroupCard(stringResource(R.string.settings_altitude_title), Icons.Filled.Landscape) {
                ClickRow("Altimeter Pressure sensor", if (s.pressureAvailable) stringResource(R.string.PREF_PRESSURE_SENSOR_AVAILABLE) else stringResource(R.string.PREF_PRESSURE_SENSOR_NOT_AVAILABLE)) { }
                ClickRow("Altitude correction (WGS84)", if (s.geoidHeight != 0f) "Correction: ${s.geoidHeight}m" else "No correction") { }
            } }
        }
        if (refreshOpen) ListDialog(stringResource(R.string.REFRESH_INTERVAL_TITLE), arrayOf("Adaptative Normal (3s-30s)","Adaptative Medium","Adaptative Low","Normal (1s)","2s","5s","Save battery (10s)","Save battery (30s)"), arrayOf("103000","203000","305000","1000","2000","5000","10000","30000"), s.refreshInterval, { refreshOpen = false }, { vm.putString("REFRESH_INTERVAL", it); refreshOpen = false })
        if (wheelPresetOpen) ListDialog(stringResource(R.string.PREF_BLE_CSC_WHEEL_PRESET_TITLE), arrayOf("Custom","29\" 2.6","29\" 2.4","29\" 2.2","700c 55mm","700c 50mm","700c 45mm","700c 40mm","700c 35mm","700c 32mm","700c 30mm","700c 28mm","700c 25mm","27.5\" 2.2","27.5\" 2.4","27.5\" 2.6","650b 45mm"), arrayOf("","2366","2333","2302","2325","2293","2234","2203","2168","2155","2146","2136","2105","2183","2216","2246","2199"), s.wheelPreset, { wheelPresetOpen = false }, { vm.putString("PREF_BLE_CSC_WHEEL_PRESET", it); wheelPresetOpen = false })
        if (hrmZoneOpen) ListDialog(stringResource(R.string.PREF_BLE_HRM_ZONE_NOTIFICATION_MODE), arrayOf("Disable","Vibrate at every zone change","Vibrate entering max zone"), arrayOf("0","1","2"), s.hrmZone, { hrmZoneOpen = false }, { vm.putString("PREF_BLE_HRM_ZONE_NOTIFICATION_MODE", it); hrmZoneOpen = false })
    }
}

@Composable
private fun NavigationGroup(nav: NavController, vm: SettingsViewModel, onPickGpx: () -> Unit) {
    val ctx = LocalContext.current
    SettingsScaffold(stringResource(R.string.settings_navigation_title), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_navigation_title), Icons.Filled.Map) {
                ClickRow(stringResource(R.string.settings_nav_load_route), stringResource(R.string.settings_nav_load_route_summary)) { onPickGpx() }
                ClickRow(stringResource(R.string.settings_nav_stop), stringResource(R.string.settings_nav_stop_summary)) { }
                ClickRow(stringResource(R.string.settings_nav_export_orux), stringResource(R.string.settings_nav_export_summary)) { }
            } }
        }
    }
}

@Composable
private fun IntegrationRoot(nav: NavController, vm: SettingsViewModel) {
    SettingsScaffold(stringResource(R.string.settings_integration_title), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_integration_title), Icons.Filled.Share) {
                ClickRow(stringResource(R.string.settings_live_nextcloud), if ((vm.state.collectAsState().value.liveTracking)) "Enable" else "Disable") { nav.navigate("live_nextcloud") }
                ClickRow(stringResource(R.string.settings_live_mmt), if ((vm.state.collectAsState().value.liveMmt)) "Enable" else "Disable") { nav.navigate("live_mmt") }
            } }
            item { GroupCard(stringResource(R.string.settings_orux_integration), Icons.Filled.Map) { ClickRow(stringResource(R.string.settings_orux_integration), oruxLabel(vm.state.collectAsState().value.oruxAuto)) { nav.navigate("orux") } } }
            item { GroupCard(stringResource(R.string.settings_strava), Icons.Filled.Download) { ClickRow(stringResource(R.string.settings_strava), stravaSummary(vm.state.collectAsState().value)) { nav.navigate("strava") } } }
        }
    }
}

@Composable
private fun NextcloudGroup(nav: NavController, vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    SettingsScaffold(stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_CATEGORIE_TITLE), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_CATEGORIE_TITLE), Icons.Filled.Share) {
                SwitchRow(stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_TITLE), stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_SUMMARY), s.liveTracking) { vm.putBool("LIVE_TRACKING", it) }
                EditRow(stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_URL_TITLE), s.liveUrl) { vm.putString("LIVE_TRACKING_URL", it) }
                EditRow(stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_TOKEN_TITLE), s.liveToken) { vm.putString("LIVE_TRACKING_TOKEN", it) }
                EditRow(stringResource(R.string.PREF_LIVE_TRACKING_NEXTCLOUD_DEVICE_TITLE), s.liveDevice) { vm.putString("LIVE_TRACKING_DEVICE", it) }
            } }
        }
    }
}

@Composable
private fun MmtGroup(nav: NavController, vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    SettingsScaffold(stringResource(R.string.PREF_LIVE_TRACKING_MMT_CATEGORIE_TITLE), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.PREF_LIVE_TRACKING_MMT_CATEGORIE_TITLE), Icons.Filled.Share) {
                SwitchRow(stringResource(R.string.PREF_LIVE_TRACKING_MMT_TITLE), stringResource(R.string.PREF_LIVE_TRACKING_MMT_SUMMARY), s.liveMmt) { vm.putBool("LIVE_TRACKING_MMT", it) }
                EditRow(stringResource(R.string.PREF_LIVE_TRACKING_MMT_LOGIN_TITLE), s.liveMmtLogin) { vm.putString("LIVE_TRACKING_MMT_LOGIN", it) }
                EditRow(stringResource(R.string.PREF_LIVE_TRACKING_MMT_PASSWORD_TITLE), s.liveMmtPassword) { vm.putString("LIVE_TRACKING_MMT_PASSWORD", it) }
            } }
        }
    }
}

@Composable
private fun OruxGroup(nav: NavController, vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val oruxUrl = stringResource(R.string.PREF_ORUXMAPS_URL)
    SettingsScaffold(stringResource(R.string.settings_orux_integration), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_orux_integration), Icons.Filled.Map) {
                ClickRow(stringResource(R.string.ORUXMAPS_AUTO_TITLE), oruxLabel(s.oruxAuto)) { open = true }
                ClickRow(stringResource(R.string.PREF_ORUXMAPS), stringResource(R.string.PREF_ORUXMAPS_SUMMARY)) { openUrl(ctx, oruxUrl) }
            } }
        }
        if (open) ListDialog(stringResource(R.string.ORUXMAPS_AUTO_TITLE), arrayOf("Disable","Continue record","Start new segment","Start new track","Start new segment (<12h) or new track"), arrayOf("disable","continue","new_segment","new_track","auto"), s.oruxAuto, { open = false }, { vm.putString("ORUXMAPS_AUTO", it); open = false })
    }
}

@Composable
private fun StravaGroup(nav: NavController, vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val summary = if (s.stravaSession.isEmpty()) stringResource(R.string.strava_not_set) else stringResource(R.string.strava_set_length, s.stravaSession.length)
    SettingsScaffold(stringResource(R.string.settings_strava), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_strava), Icons.Filled.Download) {
                EditRowWithSummary(title = stringResource(R.string.PREF_STRAVA_SESSION_TITLE), summary = summary, value = s.stravaSession, onSave = { vm.putString("STRAVA_SESSION", it) })
                ClickRow(stringResource(R.string.STRAVA_AUTO_TITLE), if (s.stravaAuto == "disable") "Disable" else "At the end of the track") { open = true }
            } }
        }
        if (open) ListDialog(stringResource(R.string.STRAVA_AUTO_TITLE), arrayOf("Disable","At the end of the track"), arrayOf("disable","end_track"), s.stravaAuto, { open = false }, { vm.putString("STRAVA_AUTO", it); open = false })
    }
}

@Composable
private fun AdvancedGroup(nav: NavController, vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    SettingsScaffold(stringResource(R.string.settings_advanced_title), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_advanced_title), Icons.Filled.Memory) { SwitchRow(stringResource(R.string.PREF_DEBUG_TITLE), stringResource(R.string.PREF_DEBUG_SUMMARY), s.debug) { vm.putBool("PREF_DEBUG", it) } } }
        }
    }
}

@Composable
private fun AboutGroup(nav: NavController, vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val aboutUrl = stringResource(R.string.PREF_ABOUT_URL)
    val pebbleUrl = stringResource(R.string.PREF_PEBBLE_STORE_URL)
    SettingsScaffold(stringResource(R.string.settings_about_title), nav) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GroupCard(stringResource(R.string.settings_about_title), Icons.Filled.Info) {
                ClickRow(stringResource(R.string.PREF_ABOUT_TITLE), stringResource(R.string.PREF_ABOUT_URL)) { openUrl(ctx, aboutUrl) }
                ClickRow(stringResource(R.string.PREF_PEBBLE_STORE), stringResource(R.string.PREF_PEBBLE_STORE_SUMMARY)) { openUrl(ctx, pebbleUrl) }
                ClickRow(stringResource(R.string.PREF_CHANGE_LOG_TITLE), stringResource(R.string.PREF_CHANGE_LOG_SUMMARY)) { openChangeLogDialog(ctx) }
            } }
        }
    }
}

@Composable
private fun GroupCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun ClickRow(title: String, summary: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun EditRow(title: String, value: String, onSave: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value) }
    ClickRow(title, value, { text = value; open = true })
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { open = false; onSave(text) }) { Text(stringResource(R.string.dialog_ok)) } }, dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) } })
    }
}

@Composable
private fun EditRowWithSummary(title: String, summary: String, value: String, onSave: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value) }
    var reveal by remember { mutableStateOf(false) }
    ClickRow(title, summary, { text = value; reveal = false; open = true })
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Filled.Bluetooth else Icons.Filled.Download, contentDescription = null) }
            })
        }, confirmButton = { TextButton(onClick = { open = false; onSave(text) }) { Text(stringResource(R.string.dialog_ok)) } }, dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) } })
    }
}

@Composable
private fun ListDialog(title: String, entries: Array<String>, values: Array<String>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column { entries.forEachIndexed { i, e -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(values[i]) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = values[i] == selected, onClick = { onSelect(values[i]) }); Spacer(Modifier.width(8.dp)); Text(e) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }, dismissButton = {})
}

private fun openUrl(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
}
private fun unitsLabel(v: String) = when(v) {"0"->"Imperial (miles)";"1"->"Metric (km/h)";"2"->"Nautical/Imperial";"3"->"Nautical/Metric";"4"->"Running/Imperial";"5"->"Running/Metric";else->v}
private fun refreshLabel(v: String) = when(v){"103000"->"Adaptative Normal (3s-30s)";"203000"->"Adaptative Medium";"305000"->"Adaptative Low";"1000"->"Normal (1s)";"2000"->"2s";"5000"->"5s";"10000"->"Save battery (10s)";"30000"->"Save battery (30s)";else->v}
private fun hrmZoneLabel(v: String) = when(v){"0"->"Disable";"1"->"Vibrate at every zone change";"2"->"Vibrate entering max zone";else->v}
private fun oruxLabel(v: String) = when(v){"disable"->"Disable";"continue"->"Continue record";"new_segment"->"Start new segment";"new_track"->"Start new track";"auto"->"Start new segment (<12h) or new track";else->v}

    private fun openChangeLogDialog(ctx: android.content.Context) {
        val activity = ctx as? Activity ?: return
        ChangeLog(activity).getFullLogDialog().show()
    }

    private fun stravaSummary(s: SettingsUiState) = "Session " + (if (s.stravaSession.isEmpty()) "not set" else "set") + if (s.stravaAuto != "disable") " - Auto upload" else ""
