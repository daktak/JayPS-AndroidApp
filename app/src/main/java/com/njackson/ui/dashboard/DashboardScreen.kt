package com.njackson.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.njackson.R
import com.njackson.ui.theme.GpsDisabled
import com.njackson.ui.theme.GpsExcellent
import com.njackson.ui.theme.GpsGood
import com.njackson.ui.theme.GpsMedium
import com.njackson.ui.theme.GpsPoor
import com.njackson.utils.NumberConverter
import com.njackson.utils.Units

private val conv = NumberConverter()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onStartStop: () -> Unit,
    onMenu: (String) -> Unit,
    onLightMode: (String, String) -> Unit = { _, _ -> },
    onGoProShutter: (String, Boolean) -> Unit = { _, _ -> },
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, modifier = Modifier.padding(start = 12.dp), tint = MaterialTheme.colorScheme.primary) },
                actions = { TopMenu(onMenu) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartStop,
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(if (state.isRunning) Icons.Filled.Timer else Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null) },
                text = { Text(stringResource(if (state.isRunning) R.string.startbuttonfragment_stop else R.string.startbuttonfragment_start), style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GpsRow(state.accuracy)
            MapCard(trail = state.trail, isIndoor = state.isIndoor)
            HeroCard(state)
            StatsGrid(state)
            SensorRow(state)
            if (state.hasHrm) SensorGraphCard(title = stringResource(R.string.dashboard_heart_rate), graph = state.hrGraph, current = if (state.heartRate in 1..254) state.heartRate else null, unit = "bpm", icon = Icons.Filled.Favorite, color = MaterialTheme.colorScheme.error, emptyText = stringResource(R.string.dashboard_no_hr_data), validRange = 1..254)
            if (state.hasPower) SensorGraphCard(title = stringResource(R.string.dashboard_power), graph = state.powerGraph, current = if (state.power >= 0) state.power else null, unit = "W", icon = Icons.Filled.Bolt, color = MaterialTheme.colorScheme.secondary, emptyText = stringResource(R.string.dashboard_no_power_data), validRange = 0..2000)
            if (state.hasCadence) SensorGraphCard(title = stringResource(R.string.dashboard_cadence), graph = state.cadenceGraph, current = if (state.cadence in 1..254) state.cadence else null, unit = "rpm", icon = Icons.Filled.PedalBike, color = MaterialTheme.colorScheme.tertiary, emptyText = stringResource(R.string.dashboard_no_cadence_data), validRange = 1..254)
            if (!state.isIndoor) ElevationCard(state.altitudes)
            state.lights.forEach { light ->
                LightCard(light = light, onModeSelected = { mode -> onLightMode(light.address, mode) }, onOff = { onLightMode(light.address, "Off") })
            }
            state.gopros.forEach { gopro ->
                GoProCard(gopro = gopro, onShutter = { start -> onGoProShutter(gopro.address, start) })
            }
            if (!state.isRunning && state.distance == 0f && state.elapsedSec == 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.dashboard_ready_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.dashboard_ready_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMenu(onMenu: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                "action_settings" to stringResource(R.string.action_settings),
                "action_export_gpx" to stringResource(R.string.action_export_gpx),
                "action_export_tcx" to stringResource(R.string.MAIN_EXPORT_TCX),
                "action_load_route" to stringResource(R.string.action_load_route),
                "action_reset" to stringResource(R.string.action_reset),
                "action_share_location" to stringResource(R.string.action_share_location),
                "action_upload_strava" to stringResource(R.string.action_upload_strava)
            ).forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onMenu(id) })
            }
        }
    }
}

@Composable
private fun GpsRow(acc: Float) {
    val (label, color) = when {
        acc == 0f -> stringResource(R.string.dashboard_gps_off) to GpsDisabled
        acc <= 4f -> stringResource(R.string.altitude_status_excellent) to GpsExcellent
        acc <= 6f -> stringResource(R.string.altitude_status_good) to GpsGood
        acc <= 10f -> stringResource(R.string.altitude_status_medium) to GpsMedium
        else -> stringResource(R.string.altitude_status_poor) to GpsPoor
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.altiudefragment_gpsstatus), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCard(s: DashboardUiState) {
    val isPace = Units.isPace(s.units)
    val speedText = if (isPace) conv.convertSpeedToPace(s.speed) else conv.convertFloatToString(s.speed, 1)
    val unitText = Units.getSpeedUnits(s.units).uppercase()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (isPace) R.string.dashboard_pace else R.string.dashboard_speed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(speedText, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(unitText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(stringResource(R.string.dashboard_avg_format, if (isPace) conv.convertSpeedToPace(s.avgSpeed) else conv.convertFloatToString(s.avgSpeed, 1), unitText), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DateUtils.formatElapsedTime(s.elapsedSec.toLong()), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.dashboard_elapsed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatsGrid(s: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(Modifier.weight(1f), Icons.Filled.Route, stringResource(R.string.dashboard_distance), conv.convertFloatToString(s.distance, 2), Units.getDistanceUnits(s.units))
            StatCard(Modifier.weight(1f), Icons.Filled.Landscape, stringResource(R.string.dashboard_ascent), conv.convertFloatToString(s.ascent.toFloat(), 0), Units.getAltitudeUnits(s.units))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(Modifier.weight(1f), Icons.Filled.Timer, stringResource(R.string.dashboard_time), DateUtils.formatElapsedTime(s.elapsedSec.toLong()), "")
            StatCard(Modifier.weight(1f), Icons.Filled.Speed, stringResource(R.string.dashboard_max_speed), conv.convertFloatToString(s.maxSpeed, 1), Units.getSpeedUnits(s.units))
        }
    }
}

@Composable
private fun StatCard(mod: Modifier, icon: ImageVector, label: String, value: String, unit: String) {
    Card(modifier = mod, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                if (unit.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
            }
        }
    }
}

@Composable
private fun SensorRow(s: DashboardUiState) {
    val dash = stringResource(R.string.placeholder_dash)
    val items = buildList {
        if (s.hasHrm) add(Triple(Icons.Filled.Favorite, stringResource(R.string.dashboard_heart_rate), if (s.heartRate in 1..254) "${s.heartRate}" to "bpm" else dash to "bpm"))
        if (s.hasCadence) add(Triple(Icons.Filled.PedalBike, stringResource(R.string.dashboard_cadence), if (s.cadence in 1..254) "${s.cadence}" to "rpm" else dash to "rpm"))
        if (s.hasPower) add(Triple(Icons.Filled.Bolt, stringResource(R.string.dashboard_power), if (s.power >= 0) "${s.power}" to "W" else dash to "W"))
    }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (icon, label, pair) ->
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(pair.first, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(3.dp))
                        Text(pair.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
            }
        }
        repeat(3 - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun SensorGraphCard(title: String, graph: List<Int>, current: Int?, unit: String, icon: ImageVector, color: Color, emptyText: String, validRange: IntRange) {
    val filtered = graph.filter { it in validRange }
    val hasData = filtered.isNotEmpty()
    val dash = stringResource(R.string.placeholder_dash)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                if (current != null) Text("$current $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(if (hasData) "${filtered.minOrNull()} – ${filtered.maxOrNull()} $unit" else dash, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (hasData) {
                    TimeSeriesSparkline(values = graph, color = color, validRange = validRange, modifier = Modifier.fillMaxSize().padding(8.dp))
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightCard(light: LightInfo, onModeSelected: (String) -> Unit, onOff: () -> Unit) {
    val isOff = light.currentModeName == "Off" || light.currentMode == 0
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (light.type == "rear") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(light.name.ifEmpty { light.address }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("${light.model} • ${light.type} • ${light.address.takeLast(5)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(if (light.connected) GpsExcellent else GpsDisabled))
                Spacer(Modifier.width(8.dp))
                if (light.battery >= 0) {
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text("${light.battery}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Mode: ${light.currentModeName.ifEmpty { if (isOff) "Off" else "${light.currentMode}" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                light.availableModes.filterKeys { it != "Off" }.toList().sortedBy { it.second }.forEach { (name, _) ->
                    val selected = name == light.currentModeName
                    FilterChip(selected = selected, onClick = { onModeSelected(name) }, label = { Text(name) }, leadingIcon = if (selected) { { Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                }
            }
            Button(onClick = onOff, modifier = Modifier.fillMaxWidth(), enabled = !isOff || light.availableModes.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Off")
            }
        }
    }
}

@Composable
private fun GoProCard(gopro: GoProInfo, onShutter: (Boolean) -> Unit) {
    val isPhoto = gopro.modeName.equals("Photo", ignoreCase = true)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(gopro.name.ifEmpty { gopro.address }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("${gopro.model} • ${gopro.address.takeLast(5)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(if (gopro.connected) GpsExcellent else GpsDisabled))
                Spacer(Modifier.width(8.dp))
                if (gopro.battery >= 0) {
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text("${gopro.battery}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Mode: ${gopro.modeName.ifEmpty { "Video" }}${if (gopro.isRecording) " • Recording" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onShutter(!gopro.isRecording) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (gopro.isRecording) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White) else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = gopro.connected
            ) {
                Icon(
                    when {
                        gopro.isRecording -> Icons.Filled.Stop
                        isPhoto -> Icons.Filled.CameraAlt
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        gopro.isRecording -> "Stop"
                        isPhoto -> "Take Photo"
                        else -> "Record"
                    }
                )
            }
        }
    }
}

@Composable
private fun ElevationCard(values: List<Int>) {
    val hasData = values.any { it != 0 }
    val dash = stringResource(R.string.placeholder_dash)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Landscape, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.dashboard_elevation), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (hasData) "${values.filter { it != 0 }.minOrNull()} – ${values.filter { it != 0 }.maxOrNull()} m" else dash, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (hasData) {
                    TimeSeriesSparkline(values = values, color = MaterialTheme.colorScheme.primary, validRange = 1..99999, modifier = Modifier.fillMaxSize().padding(8.dp))
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.dashboard_no_elevation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun TimeSeriesSparkline(values: List<Int>, color: Color, validRange: IntRange, modifier: Modifier = Modifier) {
    val filtered = values.filter { it in validRange }
    if (filtered.isEmpty()) return
    val min = filtered.minOrNull()!!.toFloat()
    val max = filtered.maxOrNull()!!.toFloat()
    val range = (max - min).takeIf { it != 0f } ?: 1f
    val fill = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)))
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v ->
            val norm = if (v !in validRange) 0.5f else (v - min) / range
            androidx.compose.ui.geometry.Offset(i * step, h - norm * h * 0.85f - h * 0.07f)
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val fillPath = Path().apply {
            addPath(path)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(fillPath, brush = fill)
        drawPath(path, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.filterIndexed { i, _ -> values[i] in validRange }.forEach { drawCircle(color = color, radius = 3.dp.toPx(), center = it) }
    }
}

@Composable
private fun ElevationSparkline(values: List<Int>, modifier: Modifier = Modifier) {
    TimeSeriesSparkline(values = values, color = MaterialTheme.colorScheme.primary, validRange = 1..99999, modifier = modifier)
}
