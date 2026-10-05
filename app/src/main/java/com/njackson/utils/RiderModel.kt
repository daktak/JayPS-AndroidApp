package com.njackson.utils

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// Physics model used for trainer sim mode and virtual speed. The trainer holds a fixed
// gradient (SIM) - while ERG holds fixed watts - plus a fixed Crr; rider height/weight feed
// the aerodynamic drag (CdA) and gravity/rolling loads so the displayed speed reacts to the
// gradient slider.
object RiderModel {
    const val GRAVITY = 9.80665            // m/s²
    const val AIR_DENSITY = 1.225          // kg/m³ at sea level
    const val CRR = 0.004                  // rolling resistance coefficient, road
    const val BIKE_MASS_KG = 9.0           // bike+rider = rider weight + bike
    const val DEFAULT_HEIGHT_CM = 175
    const val DEFAULT_WEIGHT_KG = 75
    private const val MAX_SPEED_KMH = 120.0
    private const val EPS = 1e-9

    data class Params(val heightCm: Int, val weightKg: Int) {
        // Du Bois body surface area (m²)
        val bodySurfaceArea: Double by lazy { 0.007184 * weightKg.toDouble().pow(0.425) * heightCm.toDouble().pow(0.725) }
        // Frontal area fraction of BSA, normalized so a typical 75kg/175cm rider ~0.31 m²
        val cda: Double by lazy { 0.31 * bodySurfaceArea / 1.8 }
        val massKg: Double by lazy { weightKg + BIKE_MASS_KG }
    }

    fun paramsFromPrefs(heightPref: String?, weightPref: String?): Params {
        val h = heightPref?.trim()?.toIntOrNull() ?: DEFAULT_HEIGHT_CM
        val w = weightPref?.trim()?.toIntOrNull() ?: DEFAULT_WEIGHT_KG
        return Params(h.coerceIn(120, 230), w.coerceIn(30, 180))
    }

    // Solve P = m*g*(sin θ + Crr·cos θ)·v + ½·ρ·CdA·v³ for v (steady-state), θ = atan(grade).
    // Returns km/h, clamped to MAX_SPEED_KMH, 0 when no power.
    fun virtualSpeedKmh(watts: Int, gradePct: Float, p: Params): Float {
        if (watts <= 0) return 0f
        val theta = atan(gradePct / 100.0)
        val f0 = p.massKg * GRAVITY * (sin(theta) + CRR * cos(theta))
        val k = 0.5 * AIR_DENSITY * p.cda
        val effF = max(f0, 0.0)
        var v = 1.0
        repeat(80) {
            val denom = effF + k * v * v
            val next = if (denom > EPS) watts / denom else watts / EPS
            if (abs(next - v) < 0.0005) { v = next; return@repeat }
            v = next
        }
        return (v * 3.6).toFloat().coerceAtMost(MAX_SPEED_KMH.toFloat())
    }
}