package com.njackson.events.BleServiceCommand;

public class WahooTrainerControlRequest {
    private String _address;
    private int _targetPower;       // watts, 0 = not set
    private int _resistanceLevel;   // 0-100, 0 = not set
    private boolean _ergMode;       // true = ERG, false = resistance

    public WahooTrainerControlRequest(String address, int targetPower, int resistanceLevel, boolean ergMode) {
        _address = address;
        _targetPower = targetPower;
        _resistanceLevel = resistanceLevel;
        _ergMode = ergMode;
    }

    public String getAddress() { return _address; }
    public int getTargetPower() { return _targetPower; }
    public int getResistanceLevel() { return _resistanceLevel; }
    public boolean isErgMode() { return _ergMode; }
}