package com.njackson.events.BleServiceCommand;

public class WahooTrainerControlRequest {
    private String _address;
    private int _targetPower;       // watts, 0 = not set
    private int _resistanceLevel;   // 0-100, 0 = not set
    private int _grade;             // gradient in 0.1% units (e.g. +150 = +15.0%), 0 = flat
    private boolean _ergMode;       // true = ERG, false = gradient/resistance

    public WahooTrainerControlRequest(String address, int targetPower, int resistanceLevel, int grade, boolean ergMode) {
        _address = address;
        _targetPower = targetPower;
        _resistanceLevel = resistanceLevel;
        _grade = grade;
        _ergMode = ergMode;
    }

    public String getAddress() { return _address; }
    public int getTargetPower() { return _targetPower; }
    public int getResistanceLevel() { return _resistanceLevel; }
    public int getGrade() { return _grade; }
    public boolean isErgMode() { return _ergMode; }
}