package com.njackson.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("KayPS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
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
                text = { Text(if (state.isRunning) "STOP" else "START", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GpsRow(state.accuracy)
            HeroCard(state)
            StatsGrid(state)
            SensorRow(state)
            ElevationCard(state.altitudes)
            if (!state.isRunning && state.distance == 0f && state.elapsedSec == 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ready to ride", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap START — GPS + sensors will appear here live on your watch & phone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            listOf("action_settings" to "Settings", "action_export_gpx" to "Export GPX", "action_export_tcx" to "Export TCX", "action_load_route" to "Load route", "action_reset" to "Reset", "action_share_location" to "Share location", "action_upload_strava" to "Upload Strava").forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onMenu(id) })
            }
        }
    }
}

@Composable
private fun GpsRow(acc: Float) {
    val (label, color) = when {
        acc == 0f -> "GPS OFF" to GpsDisabled
        acc <= 4f -> "EXCELLENT" to GpsExcellent
        acc <= 6f -> "GOOD" to GpsGood
        acc <= 10f -> "MEDIUM" to GpsMedium
        else -> "POOR" to GpsPoor
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
        Spacer(Modifier.weight(1f))
        Text("GPS STATUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(if (isPace) "PACE" else "SPEED", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(speedText, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(unitText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("avg ${if (isPace) conv.convertSpeedToPace(s.avgSpeed) else conv.convertFloatToString(s.avgSpeed, 1)} $unitText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DateUtils.formatElapsedTime(s.elapsedSec.toLong()), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("ELAPSED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatsGrid(s: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(Modifier.weight(1f), Icons.Filled.Route, "DISTANCE", conv.convertFloatToString(s.distance, 2), Units.getDistanceUnits(s.units))
            StatCard(Modifier.weight(1f), Icons.Filled.Landscape, "ASCENT", conv.convertFloatToString(s.ascent.toFloat(), 0), Units.getAltitudeUnits(s.units))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(Modifier.weight(1f), Icons.Filled.Timer, "TIME", DateUtils.formatElapsedTime(s.elapsedSec.toLong()), "")
            StatCard(Modifier.weight(1f), Icons.Filled.Speed, "MAX SPEED", conv.convertFloatToString(s.maxSpeed, 1), Units.getSpeedUnits(s.units))
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
    val items = buildList {
        if (s.hasHrm) add(Triple(Icons.Filled.Favorite, "HEART RATE", if (s.heartRate in 1..254) "${s.heartRate}" to "bpm" else "—" to "bpm"))
        if (s.hasCadence) add(Triple(Icons.Filled.PedalBike, "CADENCE", if (s.cadence in 1..254) "${s.cadence}" to "rpm" else "—" to "rpm"))
        if (s.hasPower) add(Triple(Icons.Filled.Bolt, "POWER", if (s.power >= 0) "${s.power}" to "W" else "—" to "W"))
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
private fun ElevationCard(values: List<Int>) {
    val hasData = values.any { it != 0 }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Landscape, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text("ELEVATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (hasData) "${values.filter { it != 0 }.minOrNull()} – ${values.filter { it != 0 }.maxOrNull()} m" else "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (hasData) {
                    ElevationSparkline(values, modifier = Modifier.fillMaxSize().padding(8.dp))
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No elevation yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun ElevationSparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val filtered = values.filter { it != 0 }
    if (filtered.isEmpty()) return
    val min = filtered.minOrNull()!!.toFloat()
    val max = filtered.maxOrNull()!!.toFloat()
    val range = (max - min).takeIf { it != 0f } ?: 1f
    val primary = MaterialTheme.colorScheme.primary
    val fill = Brush.verticalGradient(listOf(primary.copy(alpha = 0.35f), primary.copy(alpha = 0.02f)))
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v ->
            val norm = if (v == 0) 0.5f else (v - min) / range
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
        drawPath(path, color = primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.filterIndexed { i, _ -> values[i] != 0 }.forEach { drawCircle(color = primary, radius = 3.dp.toPx(), center = it) }
    }
}
