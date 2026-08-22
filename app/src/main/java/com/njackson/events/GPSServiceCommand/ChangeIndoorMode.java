package com.njackson.events.GPSServiceCommand;

/**
 * Toggle indoor (no-GPS) mode at runtime.
 */
public class ChangeIndoorMode {

    private boolean _indoor;
    public boolean isIndoor() {
        return _indoor;
    }

    public ChangeIndoorMode(boolean indoor) {
        _indoor = indoor;
    }

}
