package com.njackson.ui.dashboard

import kotlin.math.roundToInt

data class LightInfo(
    val address: String,
    val name: String,
    val model: String,
    val type: String,
    val currentMode: Int,
    val currentModeName: String,
    val availableModes: Map<String, Int>,
    val connected: Boolean,
    val battery: Int,
)

data class GoProInfo(
    val address: String,
    val name: String,
    val model: String,
    val modeName: String,
    val isRecording: Boolean,
    val battery: Int,
    val connected: Boolean,
)

data class TrainerInfo(
    val address: String = "",
    val name: String = "",
    val model: String = "",
    val connected: Boolean = false,
    val instantaneousPower: Int = 0,
    val instantaneousCadence: Int = 0,
    val instantaneousSpeed: Float = 0f,
    val resistanceLevel: Int = 0,
    val targetPower: Int = 0,
    val minResistance: Int = 0,
    val maxResistance: Int = 0,
    val minPower: Int = 0,
    val maxPower: Int = 0,
    val minSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val isErgMode: Boolean = false,
    val hasControl: Boolean = false,
    val isWahooProprietary: Boolean = false,
    val isWahooProprietaryControl: Boolean = false,
) {
    fun resistancePercent(): Float = if (maxResistance > minResistance)
        (resistanceLevel - minResistance).toFloat() / (maxResistance - minResistance) else 0f

    fun estimatedWattsAtResistance(): Int {
        if (maxResistance <= minResistance || maxPower <= minPower) return 0
        val ratio = (resistanceLevel - minResistance).toFloat() / (maxResistance - minResistance)
        return (minPower + ratio * (maxPower - minPower)).roundToInt()
    }
}

data class DashboardUiState(
    val speed: Float = 0f,
    val avgSpeed: Float = 0f,
    val distance: Float = 0f,
    val elapsedSec: Int = 0,
    val totalSec: Int = 0,
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
    val hrGraph: List<Int> = List(14) { 0 },
    val powerGraph: List<Int> = List(14) { 0 },
    val cadenceGraph: List<Int> = List(14) { 0 },
    val isIndoor: Boolean = false,
    val lights: List<LightInfo> = emptyList(),
    val gopros: List<GoProInfo> = emptyList(),
    val trainer: TrainerInfo = TrainerInfo(),
)
