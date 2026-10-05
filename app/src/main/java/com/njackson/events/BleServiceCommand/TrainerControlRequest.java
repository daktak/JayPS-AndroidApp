package com.njackson.events.BleServiceCommand;

public class TrainerControlRequest {
    private String _address;
    private int _targetPower;       // watts, 0 = not set
    private int _resistanceLevel;   // unitless, 0 = not set
    private int _grade;             // gradient in 0.1% units (e.g. +150 = +15.0%), 0 = flat
    private boolean _ergMode;       // true = ERG, false = gradient/resistance
    private boolean _requestControl;

    public TrainerControlRequest(String address, int targetPower, int resistanceLevel, int grade, boolean ergMode, boolean requestControl) {
        _address = address;
        _targetPower = targetPower;
        _resistanceLevel = resistanceLevel;
        _grade = grade;
        _ergMode = ergMode;
        _requestControl = requestControl;
    }

    public String getAddress() { return _address; }
    public int getTargetPower() { return _targetPower; }
    public int getResistanceLevel() { return _resistanceLevel; }
    public int getGrade() { return _grade; }
    public boolean isErgMode() { return _ergMode; }
    public boolean isRequestControl() { return _requestControl; }
}