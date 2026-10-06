package com.njackson.utils;

import android.content.SharedPreferences;

import com.njackson.Constants;

import fr.jayps.android.AdvancedLocation;

/**
 * Bridges the stored rider profile onto {@link AdvancedLocation} for energy expenditure estimation.
 *
 * <p>Every consumer builds its own {@code AdvancedLocation} instance, and a fresh one carries no
 * profile, so each has to be fed explicitly or the export silently reports zero calories.
 */
public final class RiderPrefs {

    private RiderPrefs() {
    }

    public static void applyTo(AdvancedLocation advancedLocation, SharedPreferences prefs) {
        if (advancedLocation == null || prefs == null) {
            return;
        }
        advancedLocation.setRiderProfile(
                intPref(prefs, Constants.PREF_RIDER_AGE),
                isFemale(prefs),
                intPref(prefs, Constants.PREF_RIDER_WEIGHT),
                intPref(prefs, Constants.PREF_RIDER_HEIGHT),
                intPref(prefs, Constants.PREF_RIDER_RESTING_HR),
                intPref(prefs, Constants.PREF_BLE_HRM_HRMAX));
    }

    /** Rider profile fields are stored as strings, like the other rider and HR settings. */
    private static int intPref(SharedPreferences prefs, String key) {
        String value = null;
        try {
            value = prefs.getString(key, null);
        } catch (ClassCastException e) {
            // written by an older build as an int
            try {
                return prefs.getInt(key, 0);
            } catch (ClassCastException ignored) {
                return 0;
            }
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isFemale(SharedPreferences prefs) {
        return Constants.RIDER_SEX_FEMALE.equals(prefs.getString(Constants.PREF_RIDER_SEX,
                Constants.RIDER_SEX_MALE));
    }
}
