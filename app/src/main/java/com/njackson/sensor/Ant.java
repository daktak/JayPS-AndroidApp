package com.njackson.sensor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;

import com.njackson.application.IInjectionContainer;
import com.njackson.events.AntServiceCommand.AntSensorData;

import com.squareup.otto.Bus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Ant implements IAnt {

    private final String TAG = "PB-Ant";

    private final Context _context;
    private Bus _bus;
    private List<PccReleaseHandle> _releaseHandles = new ArrayList<>();

    public Ant(Context context) {
        _context = context;
    }

    @Override
    public void start(Set<Integer> ant_deviceNumbers, Bus bus, IInjectionContainer container) {
        _bus = bus;
        for (Integer deviceNumber : ant_deviceNumbers) {
            requestHeartRate(deviceNumber);
            requestCadence(deviceNumber);
            requestSpeed(deviceNumber);
            requestPower(deviceNumber);
            requestStride(deviceNumber);
        }
    }

    @Override
    public void stop() {
        for (PccReleaseHandle handle : _releaseHandles) {
            try {
                handle.close();
            } catch (Exception e) {
                Log.e(TAG, "stop: " + e);
            }
        }
        _releaseHandles.clear();
    }

    private AntPluginPcc.IDeviceStateChangeReceiver stateReceiver() {
        return new AntPluginPcc.IDeviceStateChangeReceiver() {
            @Override
            public void onDeviceStateChange(DeviceState deviceState) {
            }
        };
    }

    private void addHandle(PccReleaseHandle handle) {
        if (handle != null) {
            _releaseHandles.add(handle);
        }
    }

    private void requestHeartRate(final int deviceNumber) {
        addHandle(AntPlusHeartRatePcc.requestAccess(_context, deviceNumber, 0,
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>() {
                @Override
                public void onResultReceived(AntPlusHeartRatePcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
                    if (resultCode == RequestAccessResult.SUCCESS) {
                        subscribeHeartRate(result, deviceNumber);
                    } else {
                        Log.d(TAG, "HR access failed for " + deviceNumber + ": " + resultCode);
                    }
                }
            }, stateReceiver()));
    }

    private void subscribeHeartRate(AntPlusHeartRatePcc pcc, final int deviceNumber) {
        pcc.subscribeHeartRateDataEvent(new AntPlusHeartRatePcc.IHeartRateDataReceiver() {
            @Override
            public void onNewHeartRateData(long estTimestamp, EnumSet<EventFlag> eventFlags, int computedHeartRate, long heartBeatCount, BigDecimal heartBeatEventTime, AntPlusHeartRatePcc.DataState dataState) {
                AntSensorData data = new AntSensorData(String.valueOf(deviceNumber));
                data.setHeartRate(computedHeartRate);
                _bus.post(data);
            }
        });
    }

    private void requestCadence(final int deviceNumber) {
        addHandle(AntPlusBikeCadencePcc.requestAccess(_context, deviceNumber, 0, false,
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc>() {
                @Override
                public void onResultReceived(AntPlusBikeCadencePcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
                    if (resultCode == RequestAccessResult.SUCCESS) {
                        subscribeCadence(result, deviceNumber);
                    } else {
                        Log.d(TAG, "Cadence access failed for " + deviceNumber + ": " + resultCode);
                    }
                }
            }, stateReceiver()));
    }

    private void subscribeCadence(AntPlusBikeCadencePcc pcc, final int deviceNumber) {
        pcc.subscribeCalculatedCadenceEvent(new AntPlusBikeCadencePcc.ICalculatedCadenceReceiver() {
            @Override
            public void onNewCalculatedCadence(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedCadence) {
                AntSensorData data = new AntSensorData(String.valueOf(deviceNumber));
                data.setCyclingCadence(calculatedCadence.intValue());
                _bus.post(data);
            }
        });
    }

    private void requestSpeed(final int deviceNumber) {
        addHandle(AntPlusBikeSpeedDistancePcc.requestAccess(_context, deviceNumber, 0, false,
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc>() {
                @Override
                public void onResultReceived(AntPlusBikeSpeedDistancePcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
                    if (resultCode == RequestAccessResult.SUCCESS) {
                        subscribeSpeed(result, deviceNumber);
                    } else {
                        Log.d(TAG, "Speed access failed for " + deviceNumber + ": " + resultCode);
                    }
                }
            }, stateReceiver()));
    }

    private void subscribeSpeed(AntPlusBikeSpeedDistancePcc pcc, final int deviceNumber) {
        final int wheelSize = getWheelSize();
        if (wheelSize <= 0) return;
        final BigDecimal wheelCircumference = BigDecimal.valueOf(wheelSize / 1000.0);
        pcc.subscribeCalculatedSpeedEvent(new AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver(wheelCircumference) {
            @Override
            public void onNewCalculatedSpeed(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedSpeed) {
                float wheelRpm = (float) (calculatedSpeed.doubleValue() / wheelCircumference.doubleValue() * 60.0);
                AntSensorData data = new AntSensorData(String.valueOf(deviceNumber));
                data.setCyclingWheelRpm(wheelRpm);
                _bus.post(data);
            }
        });
    }

    private void requestPower(final int deviceNumber) {
        addHandle(AntPlusBikePowerPcc.requestAccess(_context, deviceNumber, 0,
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikePowerPcc>() {
                @Override
                public void onResultReceived(AntPlusBikePowerPcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
                    if (resultCode == RequestAccessResult.SUCCESS) {
                        subscribePower(result, deviceNumber);
                    } else {
                        Log.d(TAG, "Power access failed for " + deviceNumber + ": " + resultCode);
                    }
                }
            }, stateReceiver()));
    }

    private void subscribePower(AntPlusBikePowerPcc pcc, final int deviceNumber) {
        pcc.subscribeCalculatedPowerEvent(new AntPlusBikePowerPcc.ICalculatedPowerReceiver() {
            @Override
            public void onNewCalculatedPower(long estTimestamp, EnumSet<EventFlag> eventFlags, AntPlusBikePowerPcc.DataSource dataSource, BigDecimal calculatedPower) {
                AntSensorData data = new AntSensorData(String.valueOf(deviceNumber));
                data.setPower(calculatedPower.intValue());
                _bus.post(data);
            }
        });
    }

    private void requestStride(final int deviceNumber) {
        addHandle(AntPlusStrideSdmPcc.requestAccess(_context, deviceNumber, 0,
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusStrideSdmPcc>() {
                @Override
                public void onResultReceived(AntPlusStrideSdmPcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
                    if (resultCode == RequestAccessResult.SUCCESS) {
                        subscribeStride(result, deviceNumber);
                    } else {
                        Log.d(TAG, "Stride access failed for " + deviceNumber + ": " + resultCode);
                    }
                }
            }, stateReceiver()));
    }

    private void subscribeStride(AntPlusStrideSdmPcc pcc, final int deviceNumber) {
        pcc.subscribeInstantaneousCadenceEvent(new AntPlusStrideSdmPcc.IInstantaneousCadenceReceiver() {
            @Override
            public void onNewInstantaneousCadence(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal instantaneousCadence) {
                AntSensorData data = new AntSensorData(String.valueOf(deviceNumber));
                data.setRunningCadence(instantaneousCadence.intValue());
                _bus.post(data);
            }
        });
    }

    private int getWheelSize() {
        try {
            SharedPreferences prefs = _context.getSharedPreferences("com.njackson_preferences", Context.MODE_PRIVATE);
            return Integer.valueOf(prefs.getString("PREF_BLE_CSC_WHEEL_SIZE", "0"));
        } catch (Exception e) {
            return 0;
        }
    }
}
