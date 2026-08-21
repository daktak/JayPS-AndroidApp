package com.njackson.events.PebbleServiceCommand;

/**
 * Tells the watch to enable/disable its built-in heart rate monitor as the HR source.
 * enabled != 0 enables; 0 disables.
 */
public class HrMonitorEnable {

    public int _enabled;

    public int getEnabled() {
        return _enabled;
    }

    public HrMonitorEnable(int enabled) {
        _enabled = enabled;
    }

}
