package com.njackson.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import fr.jayps.android.AdvancedLocation;

/**
 * The lap state is what carries laps across a pause, a service restart or a process death. If the
 * codec loses a field, the rider silently loses lap times on resume, so every field is checked.
 */
public class LapStateCodecTest {

    private static AdvancedLocation.LapState fullState() {
        return new AdvancedLocation.LapState(
                3,          // lapCount
                1234.5f,    // lapDistance
                60000L,     // lapElapsedTime
                70000L,     // lapTotalElapsedTime
                12.5,       // lapAscent
                8.25f,      // lapMaxSpeed
                1700000000000L, // lapStartTime
                55000L,     // lastLapElapsedTime
                1100.75f,   // lastLapDistance
                52000L,     // bestLapElapsedTime
                220000.5,   // lapPowerSum in W.ms
                60000L,     // lapPowerElapsedTime
                1100000.25, // totalPowerSum in W.ms
                180000L);   // totalPowerElapsedTime
    }

    @Test
    public void roundTripsEveryField() {
        AdvancedLocation.LapState original = fullState();
        AdvancedLocation.LapState restored = LapStateCodec.fromJson(LapStateCodec.toJson(original));

        assertEquals(original.lapCount, restored.lapCount);
        assertEquals(original.lapDistance, restored.lapDistance, 0.001);
        assertEquals(original.lapElapsedTime, restored.lapElapsedTime);
        assertEquals(original.lapTotalElapsedTime, restored.lapTotalElapsedTime);
        assertEquals(original.lapAscent, restored.lapAscent, 0.001);
        assertEquals(original.lapMaxSpeed, restored.lapMaxSpeed, 0.001);
        assertEquals(original.lapStartTime, restored.lapStartTime);
        assertEquals(original.lastLapElapsedTime, restored.lastLapElapsedTime);
        assertEquals(original.lastLapDistance, restored.lastLapDistance, 0.001);
        assertEquals(original.bestLapElapsedTime, restored.bestLapElapsedTime);
    }

    /**
     * The power running sums are what a lap's average power is computed from, so losing them
     * across a pause silently restarts the lap's average at whatever comes next.
     */
    @Test
    public void roundTripsThePowerRunningSums() {
        AdvancedLocation.LapState original = fullState();
        AdvancedLocation.LapState restored = LapStateCodec.fromJson(LapStateCodec.toJson(original));

        assertEquals(original.lapPowerSum, restored.lapPowerSum, 0.001);
        assertEquals(original.lapPowerElapsedTime, restored.lapPowerElapsedTime);
        assertEquals(original.totalPowerSum, restored.totalPowerSum, 0.001);
        assertEquals(original.totalPowerElapsedTime, restored.totalPowerElapsedTime);
    }

    @Test
    public void defaultsThePowerSumsToZeroForValuesSavedBeforeTheyWereStored() {
        AdvancedLocation.LapState restored = LapStateCodec.fromJson("{\"lapCount\":1,\"lapDistance\":10}");

        assertEquals(0, restored.lapPowerElapsedTime);
        assertEquals(0.0, restored.lapPowerSum, 0.001);
        assertEquals(0, restored.totalPowerElapsedTime);
    }

    @Test
    public void keepsFloatingPointPrecision() {
        AdvancedLocation.LapState original = new AdvancedLocation.LapState(
                1, 0.1234567f, 1L, 1L, 0.9876543, 0.0001f, 1L, 1L, 99.9999f, 1L);
        AdvancedLocation.LapState restored = LapStateCodec.fromJson(LapStateCodec.toJson(original));

        assertEquals(original.lapDistance, restored.lapDistance, 0.000001);
        assertEquals(original.lapAscent, restored.lapAscent, 0.000001);
        assertEquals(original.lapMaxSpeed, restored.lapMaxSpeed, 0.000001);
        assertEquals(original.lastLapDistance, restored.lastLapDistance, 0.000001);
    }

    /**
     * A missing value means "no ride recorded yet" and must not be turned into a zeroed lap, or the
     * first ride would start with lap counters that look already used.
     */
    @Test
    public void missingValueYieldsNullSoTheFirstRideStartsFresh() {
        assertNull(LapStateCodec.fromJson(null));
        assertNull(LapStateCodec.fromJson(""));
    }

    @Test
    public void corruptValueYieldsNullRatherThanThrowing() {
        assertNull(LapStateCodec.fromJson("not json at all"));
    }

    /**
     * An older saved value with only some fields must still load: the missing ones fall back to
     * zero, which is the state they were in before that field existed.
     */
    @Test
    public void partialValueFillsMissingFieldsWithZero() {
        AdvancedLocation.LapState restored = LapStateCodec.fromJson("{\"lapCount\":2,\"lapDistance\":900.5}");

        assertEquals(2, restored.lapCount);
        assertEquals(900.5f, restored.lapDistance, 0.001);
        assertEquals(0, restored.lastLapElapsedTime);
        assertEquals(0f, restored.lastLapDistance, 0.001);
        assertEquals(0, restored.bestLapElapsedTime);
    }
}