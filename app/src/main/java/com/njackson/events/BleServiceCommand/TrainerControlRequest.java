package com.njackson.events.BleServiceCommand;

public class TrainerControlRequest {
    private String _address;
    private int _targetPower;       // watts, 0 = not set
    private int _resistanceLevel;   // unitless, 0 = not set
    private boolean _ergMode;       // true = ERG, false = resistance
    private boolean _requestControl;

    public TrainerControlRequest(String address, int targetPower, int resistanceLevel, boolean ergMode, boolean requestControl) {
        _address = address;
        _targetPower = targetPower;
        _resistanceLevel = resistanceLevel;
        _ergMode = ergMode;
        _requestControl = requestControl;
    }

    public String getAddress() { return _address; }
    public int getTargetPower() { return _targetPower; }
    public int getResistanceLevel() { return _resistanceLevel; }
    public boolean isErgMode() { return _ergMode; }
    public boolean isRequestControl() { return _requestControl; }
}