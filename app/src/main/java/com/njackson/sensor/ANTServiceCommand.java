package com.njackson.sensor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import com.njackson.application.IInjectionContainer;
import com.njackson.application.modules.ForApplication;
import com.njackson.events.AntServiceCommand.AntStatus;
import com.njackson.events.base.BaseStatus;
import com.njackson.events.GPSServiceCommand.GPSStatus;
import com.njackson.service.IServiceCommand;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import androidx.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

public class ANTServiceCommand implements IServiceCommand {

    private final String TAG = "PB-AntServiceCommand";

    @Inject @ForApplication Context _applicationContext;
    @Inject Bus _bus;
    @Inject SharedPreferences _sharedPreferences;
    @Inject
    @Nullable
    IAnt _ant;
    IInjectionContainer _container;
    private BaseStatus.Status _currentStatus= BaseStatus.Status.NOT_INITIALIZED;
    private boolean _registered_bus = false;
    private final int max_ant_devices = 6;

    @Override
    public void execute(IInjectionContainer container) {
        container.inject(this);
        _registered_bus = false;
        if (isAntActivated()) {
            _bus.register(this);
            _registered_bus = true;
            _container = container;
            _currentStatus = BaseStatus.Status.INITIALIZED;
        }
    }

    @Override
    public void dispose() {
        if (isAntActivated() && _registered_bus) {
            _bus.unregister(this);
            _registered_bus = false;
        }
    }

    private boolean isAntAvailable() {
        try {
            _applicationContext.getPackageManager().getPackageInfo("com.dsi.ant", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isAntActivated() {
        boolean antSet = false;
        for (int i = 1; i<=max_ant_devices; i++) {
            String antAddress = _sharedPreferences.getString("ant_address"+i, "");
            if (!antAddress.equals("")) {
                antSet = true;
            }
        }
        return isAntAvailable() && antSet;
    }

    @Override
    public BaseStatus.Status getStatus() {
        return null;
    }

    @Subscribe
    public void onGPSStatusEvent(GPSStatus event) {
        switch(event.getStatus()) {
            case STARTED:
                if(_currentStatus != BaseStatus.Status.STARTED) {
                    start();
                }
                break;
            case STOPPED:
                if(_currentStatus == BaseStatus.Status.STARTED) {
                    stop();
                }
        }
    }

    private void start() {
        Log.d(TAG, "start");

        Set<Integer> deviceNumbers = new HashSet<>();
        for (int i = 1; i<=max_ant_devices; i++) {
            String antAddress = _sharedPreferences.getString("ant_address"+i, "");
            if (!antAddress.equals("")) {
                try {
                    deviceNumbers.add(Integer.valueOf(antAddress));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid ANT device number: " + antAddress);
                }
            }
        }
        Log.d(TAG, deviceNumbers.size()+" ant sensors");
        if (deviceNumbers.size()>0) {
            _ant.start(deviceNumbers, _bus, _container);
            _currentStatus = BaseStatus.Status.STARTED;
        } else {
            _currentStatus = BaseStatus.Status.UNABLE_TO_START;
        }
        _bus.post(new AntStatus(_currentStatus));
    }

    public void stop() {
        Log.d(TAG, "stop");
        if(_currentStatus != BaseStatus.Status.STARTED) {
            Log.d(TAG, "not started, unable to stop");
            return;
        }
        _ant.stop();
        _currentStatus = BaseStatus.Status.STOPPED;
        _bus.post(new AntStatus(_currentStatus));
    }
}
