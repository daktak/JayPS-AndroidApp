package com.njackson.events.BleServiceCommand;

public class BleSensorData {
    public static final int SENSOR_NONE = 0;
    public static final int SENSOR_HRM = 1;
    public static final int SENSOR_CSC_CADENCE = 2;
    public static final int SENSOR_CSC_WHEEL_RPM = 3;
    public static final int SENSOR_RSC = 4;
    public static final int SENSOR_TEMPERATURE = 5;
    public static final int SENSOR_POWER = 6;
    public static final int SENSOR_FTMS_INDOOR_BIKE = 7;
    public static final int SENSOR_FTMS_STATUS = 8;
    public static final int SENSOR_FTMS_TRAINING_STATUS = 9;
    public static final int SENSOR_FTMS_SUPPORTED_RANGES = 10;

    private String _bleAddress = "";
    public BleSensorData(String bleAddress) {
        this._bleAddress = bleAddress;
    }

    public String getBleAddress() {
        return _bleAddress;
    }

    private int _type = SENSOR_NONE;
    public int getType() {
        return _type;
    }

    private int _heartRate = 0;
    public int getHeartRate() {
        return _heartRate;
    }
    public void setHeartRate(int heartRate) {
        this._type = SENSOR_HRM;
        this._heartRate = heartRate;
    }

    private int _cyclingCadence = 0;
    public int getCyclingCadence() {
        return _cyclingCadence;
    }
    public void setCyclingCadence(int cyclingCadence) {
        this._type = SENSOR_CSC_CADENCE;
        this._cyclingCadence = cyclingCadence;
    }
    private float _cyclingWheelRpm = 0;
    public float getCyclingWheelRpm() {
        return _cyclingWheelRpm;
    }
    public void setCyclingWheelRpm(float cyclingWheelRpm) {
        this._type = SENSOR_CSC_WHEEL_RPM;
        this._cyclingWheelRpm = cyclingWheelRpm;
    }

    private int _runningCadence = 0;
    public int getRunningCadence() {
        return _runningCadence;
    }
    public void setRunningCadence(int runningCadence) {
        this._type = SENSOR_RSC;
        this._runningCadence = runningCadence;
    }

    private double _temperature = 0;
    public double getTemperature() {
        return _temperature;
    }
    public void setTemperature(double temperature) {
        this._type = SENSOR_TEMPERATURE;
        this._temperature = temperature;
    }

    private int _power = 0;
    public int getPower() {
        return _power;
    }
    public void setPower(int power) {
        this._type = SENSOR_POWER;
        this._power = power;
    }

    // FTMS Indoor Bike Data fields
    private int _instantaneousSpeed = 0;        // 0.01 km/h
    private int _instantaneousCadence = 0;      // 0.5 rpm
    private int _instantaneousPower = 0;        // watts
    private int _resistanceLevel = 0;           // unitless
    private int _targetPower = 0;               // watts (ERG mode)
    private int _minResistance = 0;
    private int _maxResistance = 0;
    private int _minPower = 0;
    private int _maxPower = 0;
    private int _minSpeed = 0;
    private int _maxSpeed = 0;
    private boolean _hasControl = false;

    // True only when this device is a pre-FTMS Wahoo KICKR (proprietary CPS-extension control).
    // Set by Ble.java ONLY in the Wahoo fallback path; genuine FTMS trainers keep this false so
    // the controls keep routing through the standard FTMS control point (FTMS takes precedence).
    private boolean _isWahooProprietaryControl = false;
    public boolean getWahooProprietaryControl() { return _isWahooProprietaryControl; }
    public void setWahooProprietaryControl(boolean v) { _isWahooProprietaryControl = v; }

    public int getInstantaneousSpeed() { return _instantaneousSpeed; }
    public void setInstantaneousSpeed(int v) { _instantaneousSpeed = v; }
    public int getInstantaneousCadence() { return _instantaneousCadence; }
    public void setInstantaneousCadence(int v) { _instantaneousCadence = v; }
    public int getInstantaneousPower() { return _instantaneousPower; }
    public void setInstantaneousPower(int v) { _instantaneousPower = v; }
    public int getResistanceLevel() { return _resistanceLevel; }
    public void setResistanceLevel(int v) { _resistanceLevel = v; }
    public int getTargetPower() { return _targetPower; }
    public void setTargetPower(int v) { _targetPower = v; }
    public int getMinResistance() { return _minResistance; }
    public void setMinResistance(int v) { _minResistance = v; }
    public int getMaxResistance() { return _maxResistance; }
    public void setMaxResistance(int v) { _maxResistance = v; }
    public int getMinPower() { return _minPower; }
    public void setMinPower(int v) { _minPower = v; }
    public int getMaxPower() { return _maxPower; }
    public void setMaxPower(int v) { _maxPower = v; }
    public int getMinSpeed() { return _minSpeed; }
    public void setMinSpeed(int v) { _minSpeed = v; }
    public int getMaxSpeed() { return _maxSpeed; }
    public void setMaxSpeed(int v) { _maxSpeed = v; }
    public boolean getHasControl() { return _hasControl; }
    public void setHasControl(boolean v) { _hasControl = v; }

    public void setFtmsIndoorBikeData(int speed, int cadence, int power, int resistance, int targetPower) {
        this._type = SENSOR_FTMS_INDOOR_BIKE;
        this._instantaneousSpeed = speed;
        this._instantaneousCadence = cadence;
        this._instantaneousPower = power;
        this._resistanceLevel = resistance;
        this._targetPower = targetPower;
    }

    public void setFtmsSupportedRanges(int minRes, int maxRes, int minPwr, int maxPwr, int minSpd, int maxSpd) {
        this._type = SENSOR_FTMS_SUPPORTED_RANGES;
        this._minResistance = minRes;
        this._maxResistance = maxRes;
        this._minPower = minPwr;
        this._maxPower = maxPwr;
        this._minSpeed = minSpd;
        this._maxSpeed = maxSpd;
    }

    public void setFtmsStatus(int status) {
        this._type = SENSOR_FTMS_STATUS;
    }

    public void setFtmsTrainingStatus(int status) {
        this._type = SENSOR_FTMS_TRAINING_STATUS;
    }
}