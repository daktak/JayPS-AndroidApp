package com.njackson.test.events.AntServiceCommand;

import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.events.AntServiceCommand.AntSensorData;

import junit.framework.TestCase;

public class AntSensorDataTest extends TestCase {

    @SmallTest
    public void test_defaults() {
        AntSensorData data = new AntSensorData("5");
        assertEquals("5", data.getAntAddress());
        assertEquals(AntSensorData.SENSOR_NONE, data.getType());
    }

    @SmallTest
    public void test_heartRate() {
        AntSensorData data = new AntSensorData("5");
        data.setHeartRate(123);
        assertEquals(AntSensorData.SENSOR_HRM, data.getType());
        assertEquals(123, data.getHeartRate());
    }

    @SmallTest
    public void test_cyclingCadence() {
        AntSensorData data = new AntSensorData("5");
        data.setCyclingCadence(90);
        assertEquals(AntSensorData.SENSOR_CSC_CADENCE, data.getType());
        assertEquals(90, data.getCyclingCadence());
    }

    @SmallTest
    public void test_wheelRpm() {
        AntSensorData data = new AntSensorData("5");
        data.setCyclingWheelRpm(45.5f);
        assertEquals(AntSensorData.SENSOR_CSC_WHEEL_RPM, data.getType());
        assertEquals(45.5f, data.getCyclingWheelRpm());
    }

    @SmallTest
    public void test_runningCadence() {
        AntSensorData data = new AntSensorData("5");
        data.setRunningCadence(170);
        assertEquals(AntSensorData.SENSOR_RSC, data.getType());
        assertEquals(170, data.getRunningCadence());
    }

    @SmallTest
    public void test_power() {
        AntSensorData data = new AntSensorData("5");
        data.setPower(250);
        assertEquals(AntSensorData.SENSOR_POWER, data.getType());
        assertEquals(250, data.getPower());
    }

    @SmallTest
    public void test_temperature() {
        AntSensorData data = new AntSensorData("5");
        data.setTemperature(21.3);
        assertEquals(AntSensorData.SENSOR_TEMPERATURE, data.getType());
        assertEquals(21.3, data.getTemperature());
    }

    @SmallTest
    public void test_only_last_type_wins() {
        AntSensorData data = new AntSensorData("5");
        data.setHeartRate(123);
        data.setPower(250);
        assertEquals(AntSensorData.SENSOR_POWER, data.getType());
        assertEquals(123, data.getHeartRate());
        assertEquals(250, data.getPower());
    }
}
