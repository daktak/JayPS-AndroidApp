package com.njackson.adapters;

import com.google.android.gms.location.DetectedActivity;

/**
 * Created by njackson on 14/01/15.
 */
public class DetectedToFitnessActivityAdapater {

    private String _activity;

    public String getActivity() {
        return _activity;
    }

    public DetectedToFitnessActivityAdapater(int activity) {
        switch (activity) {
            case DetectedActivity.ON_BICYCLE:
                break;
            case DetectedActivity.STILL:
                break;
            case DetectedActivity.WALKING:
                break;
            case DetectedActivity.RUNNING:
                break;
            case DetectedActivity.ON_FOOT:
                break;
        }
    }

}
