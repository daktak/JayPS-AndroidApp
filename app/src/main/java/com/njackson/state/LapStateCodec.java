package com.njackson.state;

import org.json.JSONException;
import org.json.JSONObject;

import fr.jayps.android.AdvancedLocation;

/**
 * Serialises {@link AdvancedLocation.LapState} to and from the JSON stored in the preferences.
 *
 * <p>Laps ride along with the rest of the ride state, so they survive a pause and a process
 * death. Keeping them in a single value rather than a dozen preference keys means adding a lap
 * field later does not grow {@link IGPSDataStore}.
 *
 * <p>Every read is defensive: a missing, empty or unparsable value yields a fresh state instead of
 * throwing, so a corrupt entry can never stop a ride from being recorded.
 */
public final class LapStateCodec {

    private static final String KEY_LAP_COUNT = "lapCount";
    private static final String KEY_LAP_DISTANCE = "lapDistance";
    private static final String KEY_LAP_ELAPSED_TIME = "lapElapsedTime";
    private static final String KEY_LAP_TOTAL_ELAPSED_TIME = "lapTotalElapsedTime";
    private static final String KEY_LAP_ASCENT = "lapAscent";
    private static final String KEY_LAP_MAX_SPEED = "lapMaxSpeed";
    private static final String KEY_LAP_START_TIME = "lapStartTime";
    private static final String KEY_LAST_LAP_ELAPSED_TIME = "lastLapElapsedTime";
    private static final String KEY_LAST_LAP_DISTANCE = "lastLapDistance";
    private static final String KEY_BEST_LAP_ELAPSED_TIME = "bestLapElapsedTime";
    private static final String KEY_LAP_POWER_SUM = "lapPowerSum";
    private static final String KEY_LAP_POWER_ELAPSED_TIME = "lapPowerElapsedTime";
    private static final String KEY_TOTAL_POWER_SUM = "totalPowerSum";
    private static final String KEY_TOTAL_POWER_ELAPSED_TIME = "totalPowerElapsedTime";

    private LapStateCodec() {
    }

    public static String toJson(AdvancedLocation.LapState state) {
        JSONObject json = new JSONObject();
        try {
            json.put(KEY_LAP_COUNT, state.lapCount);
            json.put(KEY_LAP_DISTANCE, (double) state.lapDistance);
            json.put(KEY_LAP_ELAPSED_TIME, state.lapElapsedTime);
            json.put(KEY_LAP_TOTAL_ELAPSED_TIME, state.lapTotalElapsedTime);
            json.put(KEY_LAP_ASCENT, state.lapAscent);
            json.put(KEY_LAP_MAX_SPEED, (double) state.lapMaxSpeed);
            json.put(KEY_LAP_START_TIME, state.lapStartTime);
            json.put(KEY_LAST_LAP_ELAPSED_TIME, state.lastLapElapsedTime);
            json.put(KEY_LAST_LAP_DISTANCE, (double) state.lastLapDistance);
            json.put(KEY_BEST_LAP_ELAPSED_TIME, state.bestLapElapsedTime);
            json.put(KEY_LAP_POWER_SUM, state.lapPowerSum);
            json.put(KEY_LAP_POWER_ELAPSED_TIME, state.lapPowerElapsedTime);
            json.put(KEY_TOTAL_POWER_SUM, state.totalPowerSum);
            json.put(KEY_TOTAL_POWER_ELAPSED_TIME, state.totalPowerElapsedTime);
        } catch (JSONException e) {
            return null;
        }
        return json.toString();
    }

    public static AdvancedLocation.LapState fromJson(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(value);
            return new AdvancedLocation.LapState(
                    json.optInt(KEY_LAP_COUNT, 0),
                    (float) json.optDouble(KEY_LAP_DISTANCE, 0),
                    json.optLong(KEY_LAP_ELAPSED_TIME, 0),
                    json.optLong(KEY_LAP_TOTAL_ELAPSED_TIME, 0),
                    json.optDouble(KEY_LAP_ASCENT, 0),
                    (float) json.optDouble(KEY_LAP_MAX_SPEED, 0),
                    json.optLong(KEY_LAP_START_TIME, 0),
                    json.optLong(KEY_LAST_LAP_ELAPSED_TIME, 0),
                    (float) json.optDouble(KEY_LAST_LAP_DISTANCE, 0),
                    json.optLong(KEY_BEST_LAP_ELAPSED_TIME, 0),
                    json.optDouble(KEY_LAP_POWER_SUM, 0),
                    json.optLong(KEY_LAP_POWER_ELAPSED_TIME, 0),
                    json.optDouble(KEY_TOTAL_POWER_SUM, 0),
                    json.optLong(KEY_TOTAL_POWER_ELAPSED_TIME, 0));
        } catch (JSONException e) {
            return null;
        }
    }
}