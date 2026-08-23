package com.njackson.events.AntServiceCommand;

public class AntSensorData {
    public static final int SENSOR_NONE = 0;
    public static final int SENSOR_HRM = 1;
    public static final int SENSOR_CSC_CADENCE = 2;
    public static final int SENSOR_CSC_WHEEL_RPM = 3;
    public static final int SENSOR_RSC = 4;
    public static final int SENSOR_TEMPERATURE = 5;
    public static final int SENSOR_POWER = 6;

    private String _antAddress = "";
    public AntSensorData(String antAddress) {
        this._antAddress = antAddress;
    }

    public String getAntAddress() {
        return _antAddress;
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
        _type = SENSOR_HRM;
        _heartRate = heartRate;
    }

    private int _cyclingCadence = 0;
    public int getCyclingCadence() {
        return _cyclingCadence;
    }
    public void setCyclingCadence(int cyclingCadence) {
        _type = SENSOR_CSC_CADENCE;
        _cyclingCadence = cyclingCadence;
    }

    private float _cyclingWheelRpm = 0;
    public float getCyclingWheelRpm() {
        return _cyclingWheelRpm;
    }
    public void setCyclingWheelRpm(float cyclingWheelRpm) {
        _type = SENSOR_CSC_WHEEL_RPM;
        _cyclingWheelRpm = cyclingWheelRpm;
    }

    private int _runningCadence = 0;
    public int getRunningCadence() {
        return _runningCadence;
    }
    public void setRunningCadence(int runningCadence) {
        _type = SENSOR_RSC;
        _runningCadence = runningCadence;
    }

    private double _temperature = 0;
    public double getTemperature() {
        return _temperature;
    }
    public void setTemperature(double temperature) {
        _type = SENSOR_TEMPERATURE;
        _temperature = temperature;
    }

    private int _power = 0;
    public int getPower() {
        return _power;
    }
    public void setPower(int power) {
        _type = SENSOR_POWER;
        _power = power;
    }
}
