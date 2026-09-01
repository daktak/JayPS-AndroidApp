package com.njackson.ui.dashboard

data class DashboardUiState(
    val speed: Float = 0f,
    val avgSpeed: Float = 0f,
    val distance: Float = 0f,
    val elapsedSec: Int = 0,
    val ascent: Double = 0.0,
    val maxSpeed: Float = 0f,
    val heartRate: Int = 0,
    val power: Int = -1,
    val cadence: Int = 0,
    val accuracy: Float = 0f,
    val units: Int = 1,
    val isRunning: Boolean = false,
    val altitudes: List<Int> = List(14) { 0 },
    val hasHrm: Boolean = false,
    val hasPower: Boolean = false,
    val hasCadence: Boolean = false,
    val trail: List<TrailPoint> = emptyList(),
)
