package com.njackson.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.njackson.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapCard(trail: List<TrailPoint>, isIndoor: Boolean = false, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (isIndoor) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.indoor_mode_map_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.HIKEBIKEMAP)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            isTilesScaledToDpi = true
        }
    }
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    val polyColor = MaterialTheme.colorScheme.primary.toArgb()
    val hasTrail = trail.isNotEmpty()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.map_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        !hasTrail -> stringResource(R.string.map_no_track)
                        trail.size == 1 -> stringResource(R.string.map_one_point)
                        else -> stringResource(R.string.map_points, trail.size)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasTrail) {
                    IconButton(onClick = {
                        trail.lastOrNull()?.let { pt ->
                            map.controller.animateTo(GeoPoint(pt.lat, pt.lon))
                        }
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = stringResource(R.string.map_center), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { map },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    update = { mv ->
                        mv.overlays.removeAll { it is Polyline }
                        if (trail.size >= 2) {
                            val line = Polyline().apply {
                                setPoints(trail.map { GeoPoint(it.lat, it.lon) })
                                outlinePaint.color = polyColor
                                outlinePaint.strokeWidth = 7f
                                outlinePaint.isAntiAlias = true
                            }
                            mv.overlays.add(line)
                        } else if (trail.size == 1) {
                            val line = Polyline().apply {
                                setPoints(listOf(GeoPoint(trail[0].lat, trail[0].lon), GeoPoint(trail[0].lat, trail[0].lon)))
                                outlinePaint.color = polyColor
                                outlinePaint.strokeWidth = 9f
                            }
                            mv.overlays.add(line)
                        }
                        if (trail.isNotEmpty()) {
                            val last = trail.last()
                            mv.controller.animateTo(GeoPoint(last.lat, last.lon))
                        }
                        mv.invalidate()
                    }
                )
                if (!hasTrail) {
                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.map_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .padding(0.dp)
                        )
                    }
                }
            }
        }
    }
}


