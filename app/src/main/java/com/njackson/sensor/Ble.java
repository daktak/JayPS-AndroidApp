package com.njackson.sensor;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.njackson.Constants;
import com.njackson.application.IInjectionContainer;
import com.njackson.events.BleServiceCommand.BleSensorData;
import com.njackson.events.BleServiceCommand.GoProControlRequest;
import com.njackson.events.BleServiceCommand.GoProState;
import com.njackson.events.BleServiceCommand.LightControlRequest;
import com.njackson.events.BleServiceCommand.LightState;
import com.njackson.events.BleServiceCommand.TrainerControlRequest;
import com.njackson.events.BleServiceCommand.WahooTrainerControlRequest;
import com.njackson.events.GPSServiceCommand.GPSStatus;
import com.njackson.events.base.BaseStatus;
import com.njackson.utils.time.ITimer;
import com.njackson.utils.time.ITimerHandler;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.json.JSONObject;
import org.json.JSONException;

import javax.inject.Inject;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Ble implements IBle, ITimerHandler {

    private final String TAG = "PB-Ble";

    private final Context _context;
    private Bus _bus;
    private Csc _csc;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(BLESampleGattAttributes.HEART_RATE_MEASUREMENT);
    public final static UUID UUID_CSC_MEASUREMENT = UUID.fromString(BLESampleGattAttributes.CSC_MEASUREMENT);
    public final static UUID UUID_RSC_MEASUREMENT = UUID.fromString(BLESampleGattAttributes.RSC_MEASUREMENT);
    public final static UUID UUID_BATTERY_LEVEL = UUID.fromString(BLESampleGattAttributes.BATTERY_LEVEL);
    public final static UUID UUID_TEMPERATURE_MEASUREMENT = UUID.fromString(BLESampleGattAttributes.TEMPERATURE_MEASUREMENT);
    public final static UUID UUID_CYCLING_POWER_MEASUREMENT = UUID.fromString(BLESampleGattAttributes.CYCLING_POWER_MEASUREMENT);
    public final static UUID UUID_CYCLING_POWER_SERVICE = UUID.fromString(BLESampleGattAttributes.CYCLING_POWER_SERVICE);
    public final static UUID UUID_LIGHT_MODE = UUID.fromString(BLESampleGattAttributes.LIGHT_MODE);
    public final static UUID UUID_LIGHT_MODE_SERVICE = UUID.fromString(BLESampleGattAttributes.LIGHT_MODE_SERVICE);
    public final static UUID UUID_GOPRO_SERVICE = UUID.fromString(BLESampleGattAttributes.GOPRO_SERVICE);
    public final static UUID UUID_GOPRO_COMMAND = UUID.fromString(BLESampleGattAttributes.GOPRO_COMMAND);
    public final static UUID UUID_GOPRO_QUERY = UUID.fromString("b5f90076-aa8d-11e3-9046-0002a5d5c51b");
    public final static UUID UUID_GOPRO_RESPONSE = UUID.fromString("b5f90073-aa8d-11e3-9046-0002a5d5c51b");
    public final static UUID UUID_MODEL_NUMBER = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_DEVICE_INFO_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");

    // FTMS (Fitness Machine Service)
    public final static UUID UUID_FITNESS_MACHINE_SERVICE = UUID.fromString(BLESampleGattAttributes.FITNESS_MACHINE_SERVICE);
    public final static UUID UUID_INDOOR_BIKE_DATA = UUID.fromString(BLESampleGattAttributes.INDOOR_BIKE_DATA);
    public final static UUID UUID_FITNESS_MACHINE_CONTROL_POINT = UUID.fromString(BLESampleGattAttributes.FITNESS_MACHINE_CONTROL_POINT);
    public final static UUID UUID_FITNESS_MACHINE_STATUS = UUID.fromString(BLESampleGattAttributes.FITNESS_MACHINE_STATUS);
    public final static UUID UUID_TRAINING_STATUS = UUID.fromString(BLESampleGattAttributes.TRAINING_STATUS);
    public final static UUID UUID_SUPPORTED_RESISTANCE_LEVEL_RANGE = UUID.fromString(BLESampleGattAttributes.SUPPORTED_RESISTANCE_LEVEL_RANGE);
    public final static UUID UUID_SUPPORTED_POWER_RANGE = UUID.fromString(BLESampleGattAttributes.SUPPORTED_POWER_RANGE);
    public final static UUID UUID_SUPPORTED_SPEED_RANGE = UUID.fromString(BLESampleGattAttributes.SUPPORTED_SPEED_RANGE);

    // Wahoo proprietary Cycling Power Extension (pre-FTMS KICKR control)
    public final static UUID UUID_WAHOO_CYCLING_POWER_EXTENSION = UUID.fromString(BLESampleGattAttributes.WAHOO_CYCLING_POWER_EXTENSION);

    private final static int TIMEOUT_CONNECTGATT = 5 * 60 * 1000; // in ms

    private int _cpsCrankRevolutions = 0;
    private long _cpsLastCrankEventTime = 0;

    private boolean debug = true;
    private boolean _bleStarted = false;
    @Inject ITimer _timer;

    private Queue<BluetoothDevice> connectionQueue = new LinkedList<>();
    private Thread connectionThread;
    private Queue<BluetoothGatt> serviceDiscoveryQueue = new LinkedList<>();
    private Thread serviceDiscoveryThread;
    private ConcurrentHashMap<String, BluetoothGatt> mGatts = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, BluetoothGatt> mGattsConnectionPending = new ConcurrentHashMap<>();
    private static class PendingCharacteristicWrite { BluetoothGatt gatt; BluetoothGattCharacteristic characteristic; PendingCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c) { gatt=g; characteristic=c; } }
    private static class PendingDescriptorWrite { BluetoothGatt gatt; BluetoothGattDescriptor descriptor; PendingDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d) { gatt=g; descriptor=d; } }
    private Queue<PendingDescriptorWrite> descriptorWriteQueue = new LinkedList<>();
    private Queue<PendingCharacteristicWrite> characteristicWriteQueue = new LinkedList<>();
    private Queue<PendingCharacteristicWrite> readCharacteristicQueue = new LinkedList<>();
    private boolean allwrites = false;
    private int _nbReconnect = 0;
    private ConcurrentHashMap<BluetoothGatt, Integer> light_mode  = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> deviceModels = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> deviceBattery = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Boolean> goproRecording = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> goproMode = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Boolean> goproPending = new ConcurrentHashMap<>();

    // FTMS Trainer state
    private ConcurrentHashMap<String, Integer> trainerMinResistance = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> trainerMaxResistance = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> trainerMinPower = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> trainerMaxPower = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> trainerMinSpeed = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> trainerMaxSpeed = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Boolean> trainerHasControl = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Boolean> trainerControlRequested = new ConcurrentHashMap<>();
    private String trainerAddress = "";

    // Wahoo proprietary (pre-FTMS KICKR) state
    private ConcurrentHashMap<String, BluetoothGattCharacteristic> wahooExtensionChar = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> wahooMinPower = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> wahooMaxPower = new ConcurrentHashMap<>();

    private Set<String> _ble_addresses;

    public Ble(Context context) {
        _context = context;
        _csc = new Csc();
    }
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "Turning Bluetooth off...");
                        mBluetoothManager = null;
                        disconnectAllDevices();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "Bluetooth on");

                        if (_bleStarted) {
                            initialize();
                        }

                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "Turning Bluetooth on...");
                        break;
                }
            }
        }
    };
    @Override
    public void start(Set<String> ble_addresses, Bus bus, IInjectionContainer container) {
        Log.d(TAG, "start");

        container.inject(this);
        _bus = bus;
        try { _bus.register(this); } catch (Exception e) {}
        _bleStarted = true;
        _ble_addresses = ble_addresses;
        initialize();

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        _context.registerReceiver(mReceiver, filter);
    }
    @Override
    public void stop() {
        Log.d(TAG, "stop");
        _bleStarted = false;
        try { _bus.unregister(this); } catch (Exception e) {}
        disconnectAllDevices();

        // Unregister broadcast listeners
        _context.unregisterReceiver(mReceiver);
    }

    @Override
    public void disconnectAddress(String address) {
        if (address == null || address.isEmpty()) return;
        Log.d(TAG, "disconnectAddress " + address);
        if (_ble_addresses != null) _ble_addresses.remove(address);
        try {
            Iterator<BluetoothDevice> it = connectionQueue.iterator();
            while (it.hasNext()) {
                BluetoothDevice d = it.next();
                if (d.getAddress().equals(address)) {
                    it.remove();
                    Log.d(TAG, "removed from connectionQueue " + address);
                }
            }
        } catch (Exception e) {}
        try {
            BluetoothGatt pending = mGattsConnectionPending.remove(address);
            if (pending != null) {
                Log.d(TAG, "closing pending gatt " + address);
                try { pending.close(); } catch (Exception e) {}
            }
        } catch (Exception e) {}
        try {
            BluetoothGatt gatt = mGatts.remove(address);
            if (gatt != null) {
                Log.d(TAG, "disconnect active gatt " + address);
                try { start_stop_handler(gatt, false); } catch (Exception e) {}
                try { gatt.disconnect(); } catch (Exception e) {}
                try { gatt.close(); } catch (Exception e) {}
                try { light_mode.remove(gatt); } catch (Exception e) {}
                try {
                    Iterator<PendingDescriptorWrite> dit = descriptorWriteQueue.iterator();
                    while (dit.hasNext()) if (dit.next().gatt == gatt) dit.remove();
                    Iterator<PendingCharacteristicWrite> cit = characteristicWriteQueue.iterator();
                    while (cit.hasNext()) if (cit.next().gatt == gatt) cit.remove();
                    Iterator<PendingCharacteristicWrite> rit = readCharacteristicQueue.iterator();
                    while (rit.hasNext()) if (rit.next().gatt == gatt) rit.remove();
                } catch (Exception e) {}
                try { deviceBattery.remove(address); } catch (Exception e) {}
                try { goproRecording.remove(address); } catch (Exception e) {}
                try { goproMode.remove(address); } catch (Exception e) {}
                try { goproPending.remove(address); } catch (Exception e) {}
                try { triggerNextWrite(); } catch (Exception e) {}
            }
        } catch (Exception e) { Log.w(TAG, "disconnectAddress failed "+e); }
        try { goproPending.remove(address); } catch (Exception e) {}
        try { goproRecording.remove(address); } catch (Exception e) {}
    }


    public void initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) _context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return;
        }

        Log.d(TAG, "initialize OK");

        if (mBluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
            Log.e(TAG, "Bluetooth is not ON");
            // new attempt to connect will be done when receiving BluetoothAdapter.ACTION_STATE_CHANGED
            return;
        } else {
            if (mBluetoothAdapter == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }

            for (String _ble_address: _ble_addresses) {
                Log.d(TAG, "initConnection " + _ble_address);
                BluetoothDevice device = null;
                if (!_ble_address.equals("")) {
                    device = mBluetoothAdapter.getRemoteDevice(_ble_address);
                    if (device == null) {
                        Log.w(TAG, "Device not found. Unable to connect.");
                        return;
                    }
                    connectionQueue.add(device);
                }
            }

            if (connectionQueue.size() > 0) {
                if (connectionThread == null) {
                    connectionThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            connectionLoop();
                            //                    connectionThread.interrupt();
                            //                    connectionThread = null;
                        }
                    });

                    connectionThread.start();
                }
            }
        }
    }

    private void connectionLoop() {
        while(connectionThread != null) {
            while (!connectionQueue.isEmpty()) {
                BluetoothDevice device = connectionQueue.poll();
                Log.d(TAG, "connectionLoop next device " + device.getAddress().toString());
                // Official doc: Cancel the current device discovery process.
                // An application should always call cancel discovery even if it did not directly request a discovery, just to be sure.
                mBluetoothAdapter.cancelDiscovery();

                // force new timer (to cancel connectGatt() if its callback is not called in TIMEOUT_CONNECTGATT ms)
                _timer.cancel();
                _timer.setTimer(TIMEOUT_CONNECTGATT, this);

                BluetoothGatt gatt = device.connectGatt(_context, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        Log.d(TAG, display(gatt) + " onConnectionStateChange status=" + status + " newState=" + newState);

                        if (status == 133) {
                            // http://stackoverflow.com/questions/21021429/bluetoothlowenergy-range-issue-android
                            Log.d(TAG, display(gatt) + " status=133, not in range?");
                        }

                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            _nbReconnect = 0;
                            // TODO(jay) post something?
                            //broadcastUpdate(ACTION_GATT_CONNECTED);
                            if (debug) Log.i(TAG, display(gatt) + " Connected to GATT server.");

                            mGattsConnectionPending.remove(gatt.getDevice().getAddress());
                            Log.d(TAG, "after remove, mGattsConnectionPending.size:" + mGattsConnectionPending.size());

                            // Attempts to discover services after successful connection.
                            //boolean discovery = mBluetoothGatt.discoverServices();
                            serviceDiscoveryQueue.add(gatt);

                            Log.d(TAG, "connectionQueue.size=" + connectionQueue.size());
                            //if (connectionQueue.isEmpty()) {
                            initServiceDiscovery();
                            //}


                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            if (debug) Log.i(TAG, display(gatt) + " Disconnected from GATT server.");
                            try { postLightState(gatt); postGoProState(gatt); } catch (Exception e) {}
                            gatt.close();
                            mGatts.remove(gatt.getDevice().getAddress());
                            try { postLightState(gatt); postGoProState(gatt); } catch (Exception e) {}
                            if (_bleStarted) {
                                reconnectLater(gatt);
                            }
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        Log.d(TAG, "onServicesDiscovered");
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (debug) Log.i(TAG, display(gatt) + " discovered GATT services.");
                            // TODO(jay) post something?
                            //broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                            displayGattServices(gatt);
                        } else {
                            if (debug) Log.w(TAG, display(gatt) + " onServicesDiscovered received: " + status);
                        }
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt,
                                                     BluetoothGattCharacteristic characteristic,
                                                     int status) {
                        readCharacteristicQueue.poll();

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            String msg = decodeCharacteristic(gatt, characteristic);
                            if (debug) Log.d(TAG, display(gatt, characteristic) + " onCharacteristicRead status=" + status + msg);
                        } else {
                            Log.d(TAG, display(gatt, characteristic) + " onCharacteristicRead error: " + status);
                        }
                        if (!readCharacteristicQueue.isEmpty()) {
                            PendingCharacteristicWrite n = readCharacteristicQueue.peek();
                            n.gatt.readCharacteristic(n.characteristic);
                        }
                    }

                    @Override
                    public void onCharacteristicChanged(BluetoothGatt gatt,
                                                        BluetoothGattCharacteristic characteristic) {
                        String msg = decodeCharacteristic(gatt, characteristic);
                        if (debug) Log.d(TAG, display(gatt) + " onCharacteristicChanged" + display(characteristic) + " " + msg);
                    }

                    @Override
                    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                        Log.d(TAG, display(gatt) + " onMtuChanged mtu=" + mtu + " status=" + status);
                    }

                    @Override
                    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                        Log.d(TAG, display(gatt) + " onReadRemoteRssi rssi=" + rssi + " status=" + status);
                    }

                    @Override
                    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, display(gatt) + " Callback: Wrote GATT Descriptor successfully.");
                        } else {
                            Log.d(TAG, display(gatt) + " Callback: Error writing GATT Descriptor: " + status);
                        }
                        descriptorWriteQueue.poll();
                        if (!descriptorWriteQueue.isEmpty()) {
                            Log.d(TAG, display(gatt) + " write next descriptor");
                            PendingDescriptorWrite n = descriptorWriteQueue.peek();
                            n.gatt.writeDescriptor(n.descriptor);
                        } else if (!characteristicWriteQueue.isEmpty()) {
                            Log.d(TAG, display(gatt) + " write next characteristic");
                            PendingCharacteristicWrite n = characteristicWriteQueue.peek();
                            n.gatt.writeCharacteristic(n.characteristic);
                        } else if (!readCharacteristicQueue.isEmpty()) {
                            Log.d(TAG, display(gatt) + " no more descriptor, next read");
                            PendingCharacteristicWrite n = readCharacteristicQueue.peek();
                            n.gatt.readCharacteristic(n.characteristic);
                        }
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, display(gatt) + " Callback: Wrote GATT Characteristic successfully.");
                        } else {
                            Log.d(TAG, display(gatt) + " Callback: Error writing GATT Characteristic: " + status);
                        }
                        characteristicWriteQueue.poll();
                        if (!descriptorWriteQueue.isEmpty()) {
                            Log.d(TAG, display(gatt) + " write next descriptor");
                            PendingDescriptorWrite n = descriptorWriteQueue.peek();
                            n.gatt.writeDescriptor(n.descriptor);
                        } else if (!characteristicWriteQueue.isEmpty()) {
                            Log.d(TAG, display(gatt) + " write next characteristic");
                            PendingCharacteristicWrite n = characteristicWriteQueue.peek();
                            n.gatt.writeCharacteristic(n.characteristic);
                        } else if (!readCharacteristicQueue.isEmpty()) {
                            Log.d(TAG, display(gatt) + " no more descriptor, next read");
                            PendingCharacteristicWrite n = readCharacteristicQueue.peek();
                            n.gatt.readCharacteristic(n.characteristic);
                        }
                    }

                });

                mGatts.put(gatt.getDevice().getAddress(), gatt);
                //Log.d(TAG, "put " + display(gatt) + " into mGatts, size:" + mGatts.size());
                mGattsConnectionPending.put(gatt.getDevice().getAddress(), gatt);
                ///Log.d(TAG, "put " + display(gatt) + " into mGattsConnectionPending, size:" + mGattsConnectionPending.size());

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
        }
    }

    public void setLightMode(BluetoothGatt gatt, Boolean light_status) {
        int newMode = 0;
        String newModeString = "Off";
        String device = "";
        Boolean found = true;
        try {
            device = gatt.getDevice().getName().toString();
        } catch (Exception e) {
            found = false;
        }

        if (found) {
            if (light_status) {
                switch (device) {
                    case "Flare RT":
                    case "ION 200 RT":
                    case "ION PRO RT":
                        newModeString = "Day Flash";
                        break;
                    default:
                        // generic fallback for unknown Bontrager/Trek and other lights (e.g., Ion 100, Flare City)
                        // try Day Flash first, else High
                        newModeString = "Day Flash";
                        break;
                }
            }

            try {
                JSONObject LIGHT_MODES_JSON = new JSONObject(BLESampleGattAttributes.LIGHT_MODES_JSON);
                JSONObject json = null;
                if (LIGHT_MODES_JSON.has(device)) json = (JSONObject) LIGHT_MODES_JSON.get(device);
                else if (LIGHT_MODES_JSON.has("Generic")) json = (JSONObject) LIGHT_MODES_JSON.get("Generic");
                if (json == null) return;
                String val = json.optString(newModeString);
                if (val == null || val.isEmpty()) val = json.optString("High", "1");
                try {
                    newMode = Integer.parseInt(val);
                } catch (Exception e) {
                    Log.i(TAG, "Unable to load light mode for "+device+" : "+e);
                    return;
                }
            } catch (JSONException e) {
                Log.w(TAG, "Unable to load light JSON: "+e);
            }

            final BluetoothGattCharacteristic gattChar = getCharacter(gatt, UUID_LIGHT_MODE_SERVICE, UUID_LIGHT_MODE, "LIGHT MODE");
            if (gattChar != null) {
                Integer current_mode = light_mode.get(gatt);
                if (current_mode==null) {
                    light_mode.put(gatt, 0);
                    current_mode = 0;
                }
                if ((newMode==0)||current_mode.equals(0)) {
                    Log.i(TAG, String.format("Setting light mode %d",newMode));
                    gattChar.setValue(newMode, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    characteristicWriteQueue.add(new PendingCharacteristicWrite(gatt, gattChar));
                    light_mode.put(gatt, newMode);
                    postLightState(gatt);
                } else if (newMode != 0) {
                    light_mode.put(gatt, newMode);
                    postLightState(gatt);
                }
            }
        }
    }

    public void setLightMode(String address, String modeName) {
        BluetoothGatt gatt = mGatts.get(address);
        if (gatt == null) gatt = mGattsConnectionPending.get(address);
        if (gatt != null) setLightMode(gatt, modeName);
    }

    public void setLightMode(BluetoothGatt gatt, String modeName) {
        String device = "";
        try { device = gatt.getDevice().getName().toString(); } catch (Exception e) { return; }
        try {
            JSONObject LIGHT_MODES_JSON = new JSONObject(BLESampleGattAttributes.LIGHT_MODES_JSON);
            JSONObject json = null;
            if (LIGHT_MODES_JSON.has(device)) json = (JSONObject) LIGHT_MODES_JSON.get(device);
            else if (LIGHT_MODES_JSON.has("Generic")) json = (JSONObject) LIGHT_MODES_JSON.get("Generic");
            if (json == null) return;
            String val = json.optString(modeName);
            if (val == null || val.isEmpty()) return;
            int newMode = Integer.parseInt(val);
            BluetoothGattCharacteristic gattChar = getCharacter(gatt, UUID_LIGHT_MODE_SERVICE, UUID_LIGHT_MODE, "LIGHT MODE");
            if (gattChar != null) {
                Log.i(TAG, String.format("User setting light %s mode %s -> %d", device, modeName, newMode));
                gattChar.setValue(newMode, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                // immediate write if possible
                if (descriptorWriteQueue.isEmpty() && readCharacteristicQueue.isEmpty()) {
                    if (characteristicWriteQueue.isEmpty()) {
                        gatt.writeCharacteristic(gattChar);
                    } else {
                        characteristicWriteQueue.add(new PendingCharacteristicWrite(gatt, gattChar));
                    }
                } else {
                    characteristicWriteQueue.add(new PendingCharacteristicWrite(gatt, gattChar));
                }
                light_mode.put(gatt, newMode);
                postLightState(gatt);
            }
        } catch (Exception e) { Log.w(TAG, "setLightMode string failed "+e); }
    }

    private void postLightState(BluetoothGatt gatt) {
        if (_bus == null || gatt == null) return;
        // only lights have 71261000 service - GoPro/HRM must not appear as light card
        if (gatt.getService(UUID_LIGHT_MODE_SERVICE) == null) return;
        String addr = gatt.getDevice().getAddress();
        String name = "";
        try { name = gatt.getDevice().getName(); } catch (Exception e) {}
        if (name == null) name = addr;
        String model = deviceModels.get(addr);
        if (model == null) model = name;
        String type = "unknown";
        String low = model.toLowerCase();
        if (low.contains("flare")) type = "rear";
        else if (low.contains("ion") || low.contains("circuit")) type = "front";
        int cur = light_mode.get(gatt) != null ? light_mode.get(gatt) : -1;
        String curName = "";
        int batt = deviceBattery.get(addr) != null ? deviceBattery.get(addr) : -1;
        try {
            JSONObject LIGHT_MODES_JSON = new JSONObject(BLESampleGattAttributes.LIGHT_MODES_JSON);
            JSONObject json = null;
            if (LIGHT_MODES_JSON.has(name)) json = (JSONObject) LIGHT_MODES_JSON.get(name);
            else if (LIGHT_MODES_JSON.has(model)) json = (JSONObject) LIGHT_MODES_JSON.get(model);
            else if (LIGHT_MODES_JSON.has("Generic")) json = (JSONObject) LIGHT_MODES_JSON.get("Generic");
            Map<String,Integer> map = new java.util.HashMap<>();
            if (json != null) {
                java.util.Iterator<String> it = json.keys();
                while (it.hasNext()) { String k = it.next(); map.put(k, json.getInt(k)); if (json.getInt(k)==cur) curName=k; }
            }
            if (curName.isEmpty() && cur==0) curName="Off";
            _bus.post(new LightState(addr, name, model, type, cur, curName, map, mGatts.containsKey(addr), batt));
        } catch (Exception e) { Log.w(TAG, "postLightState failed "+e); }
    }

    private void postGoProState(BluetoothGatt gatt) {
        if (_bus == null || gatt == null) return;
        if (gatt.getService(UUID_GOPRO_SERVICE) == null) return;
        String addr = gatt.getDevice().getAddress();
        String name = "";
        try { name = gatt.getDevice().getName(); } catch (Exception e) {}
        if (name == null) name = addr;
        String model = deviceModels.get(addr);
        if (model == null) model = name;
        int batt = deviceBattery.get(addr) != null ? deviceBattery.get(addr) : -1;
        boolean rec = goproRecording.get(addr) != null ? goproRecording.get(addr) : false;
        String mode = goproMode.get(addr);
        if (mode == null) mode = "Video";
        boolean connected = mGatts.containsKey(addr);
        try { _bus.post(new GoProState(addr, name, model, mode, rec, batt, connected)); } catch (Exception e) { Log.w(TAG, "postGoProState failed "+e); }
    }

    @Subscribe
    public void onLightControl(LightControlRequest req) { setLightMode(req.getAddress(), req.getModeName()); }

    @Subscribe
    public void onGoProControl(GoProControlRequest req) {
        BluetoothGatt gatt = mGatts.get(req.getAddress());
        if (gatt == null) gatt = mGattsConnectionPending.get(req.getAddress());
        if (gatt != null) {
            if (gatt.getService(UUID_GOPRO_SERVICE) != null) {
                setGoProRecording(gatt, req.isStart());
                goproPending.remove(req.getAddress());
            } else {
                goproPending.put(req.getAddress(), req.isStart());
            }
            goproRecording.put(req.getAddress(), req.isStart());
            postGoProState(gatt);
            triggerNextWrite();
            return;
        }
        goproRecording.put(req.getAddress(), req.isStart());
        goproPending.put(req.getAddress(), req.isStart());
        try {
            String cachedName = "";
            String cachedModel = deviceModels.get(req.getAddress());
            if (cachedModel == null) cachedModel = "";
            int cachedBatt = deviceBattery.get(req.getAddress()) != null ? deviceBattery.get(req.getAddress()) : -1;
            String cachedMode = goproMode.get(req.getAddress());
            if (cachedMode == null) cachedMode = "Video";
            try { _bus.post(new GoProState(req.getAddress(), cachedName, cachedModel, cachedMode, req.isStart(), cachedBatt, false)); } catch (Exception e) {}
        } catch (Exception e) {}
        if (mBluetoothAdapter != null && _bleStarted) {
            try {
                BluetoothDevice dev = mBluetoothAdapter.getRemoteDevice(req.getAddress());
                if (!mGattsConnectionPending.containsKey(req.getAddress())) {
                    connectionQueue.add(dev);
                    ensureConnectionThread();
                }
            } catch (Exception e) { Log.w(TAG, "GoPro wake queue failed "+e); }
        }
    }

    @Subscribe
    public void onTrainerControl(TrainerControlRequest req) {
        handleTrainerControlRequest(req);
    }

    @Subscribe
    public void onWahooTrainerControl(WahooTrainerControlRequest req) {
        handleWahooTrainerControlRequest(req);
    }

    private void ensureConnectionThread() {
        if (connectionThread == null) {
            connectionThread = new Thread(new Runnable() {
                @Override public void run() { connectionLoop(); }
            });
            connectionThread.start();
        }
    }

    @Subscribe
    public void onGPSStatus(GPSStatus e) {
        boolean start;
        if (e.getStatus() == BaseStatus.Status.STARTED) start = true;
        else if (e.getStatus() == BaseStatus.Status.STOPPED) start = false;
        else return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        boolean allowLights = prefs.getBoolean(Constants.PREF_AUTOSTART_LIGHTS, true);
        boolean allowGoPro = prefs.getBoolean(Constants.PREF_AUTOSTART_GOPRO, true);
        for (BluetoothGatt gatt : mGatts.values()) {
            if (gatt.getService(UUID_GOPRO_SERVICE) != null && allowGoPro) {
                setGoProRecording(gatt, start);
                goproRecording.put(gatt.getDevice().getAddress(), start);
                postGoProState(gatt);
            }
            if (gatt.getService(UUID_LIGHT_MODE_SERVICE) != null && allowLights) {
                setLightMode(gatt, start);
            }
        }
        triggerNextWrite();
    }

    private void triggerNextWrite() {
        if (!descriptorWriteQueue.isEmpty()) {
            PendingDescriptorWrite d = descriptorWriteQueue.peek();
            try { d.gatt.writeDescriptor(d.descriptor); } catch (Exception ex) {}
        } else if (!characteristicWriteQueue.isEmpty()) {
            PendingCharacteristicWrite w = characteristicWriteQueue.peek();
            try { w.gatt.writeCharacteristic(w.characteristic); } catch (Exception ex) {}
        } else if (!readCharacteristicQueue.isEmpty()) {
            PendingCharacteristicWrite r = readCharacteristicQueue.peek();
            try { r.gatt.readCharacteristic(r.characteristic); } catch (Exception ex) {}
        }
    }

    public void disconnectAllDevices() {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "disconnectAllDevices BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "disconnectAllDevices mGatts.size:" + mGatts.size());
        Iterator<Map.Entry<String, BluetoothGatt>> iterator = mGatts.entrySet().iterator();
        while (iterator.hasNext()) {
            BluetoothGatt gatt = iterator.next().getValue();
            Log.d(TAG, "disconnect" + display(gatt));
            if (gatt != null) {
                start_stop_handler(gatt, false);
                gatt.disconnect();
                gatt.close();
                //gatt = null;
            }
        }
        mGatts.clear();
        if (connectionThread != null) {
            connectionThread.interrupt();
            connectionThread = null;
        }
    }

    private void reconnectLater(BluetoothGatt gatt) {
        // Note: reconnectLater can be called just after STATE_TURNING_OFF
        if (mBluetoothAdapter == null || mBluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
            return;
        }

        _nbReconnect++;
        try {
            int sleep;
            if (_nbReconnect < 5) {
                sleep = 5;
            } else if(_nbReconnect <= 20) {
                sleep = 15;
            } else {
                sleep = 60;
            }
            Log.w(TAG, display(gatt) + " reconnectLater, _nbReconnect: " + _nbReconnect + " wait " + sleep + "s");
            Thread.sleep(1000 * sleep);
        } catch (InterruptedException e) {
        }
        if (_bleStarted) {

            if (mGattsConnectionPending.containsKey(gatt.getDevice().getAddress())) {
                Log.w(TAG, display(gatt) + " reconnectLater already in mGattsConnectionPending, skip it");
            } else {
                Log.w(TAG, display(gatt) + " reconnectLater connectionQueue.add");
                connectionQueue.add(gatt.getDevice());
                ensureConnectionThread();
            }
        }
    }

    private void initServiceDiscovery() {
        Log.d(TAG, "initServiceDiscovery");
        if(serviceDiscoveryThread == null){
            serviceDiscoveryThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    serviceDiscovery();

                    //Log.d(TAG, "before serviceDiscoveryThread.interrupt");
                    serviceDiscoveryThread.interrupt();
                    serviceDiscoveryThread = null;
                }
            });

            serviceDiscoveryThread.start();
        }
    }

    private void serviceDiscovery() {
        Log.d(TAG, "serviceDiscovery start");
        while(!serviceDiscoveryQueue.isEmpty()){
            BluetoothGatt gatt = serviceDiscoveryQueue.poll();
            Log.d(TAG, "serviceDiscovery next device " + display(gatt));
            gatt.discoverServices();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e){}
        }
        Log.d(TAG, "serviceDiscovery end");
    }

    private String decodeCharacteristic(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
        String res = "";
        //if (debug) Log.d(TAG, "decodeCharacteristic() "+display(gatt, characteristic));

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            Log.d(TAG, String.format("flags: %d|%s", flags, Integer.toBinaryString(flags)));
            int sensorContactStatus = (flags & 0x06) >> 1;
            // 0,1    Sensor Contact feature is not supported in the current connection
            // 2     Sensor Contact feature is supported, but contact is not detected
            // 3     Sensor Contact feature is supported and contact is detected
            Log.d(TAG, "sensorContactStatus=" + sensorContactStatus);
            /*byte[] values = characteristic.getValue();
            String tmp = "";
            for(int i=0; i<values.length; i++) {
                tmp += String.format("|%d(%02X)", values[i], values[i]);
            }
            Log.d(TAG, "characteristic HRM=" + tmp);*/
            int offset = 1;
            int format = -1;
            if ((flags & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                offset += 2;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                offset += 1;
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            res = String.format("Received heart rate: %d", heartRate);

            if ((flags & (1 << 3)) != 0) {
                // calories present
                int energy = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;
                Log.d(TAG, "Received energy: " + energy);
            }
            if ( (flags & (1 << 4)) != 0) {
                // RR interval.
                int rrCount = ((characteristic.getValue()).length - offset) / 2;
                int[] rrIntervals = new int[rrCount];
                String tmp = "";
                for (int i = 0; i < rrCount; i++){
                    rrIntervals[i] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                    offset += 2;
                    tmp += " " + rrIntervals[i];
                }
                Log.d(TAG, "Received rrIntervals: " + tmp);
            }

            BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
            sensorData.setHeartRate(heartRate);
            //sensorData.setCyclingWheelRpm(3 * heartRate); // fake values to debug csc
            _bus.post(sensorData);
        } else if (UUID_CSC_MEASUREMENT.equals(characteristic.getUuid())) {
            int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            Log.d(TAG, String.format("flags: %d|%s", flags, Integer.toBinaryString(flags)));
            /*byte[] values = characteristic.getValue();
            String tmp = "";
            for(int i=0; i<values.length; i++) {
                tmp += String.format("|%d(%02X)", values[i], values[i]);
            }
            Log.d(TAG, "characteristic CSC=" + tmp);*/
            boolean wheelRevolutionDataPresent = false;
            boolean crankRevolutionDataPresent = false;
            int cumulativeWheelRevolutions = 0;
            int lastWheelEventTime = 0;
            int cumulativeCrankRevolutions = 0;
            int lastCrankEventTime = 0;
            int offset = 0;

            if ((flags & 0x01) != 0) {
                cumulativeWheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32 , 1);
                lastWheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16 , 5);
                wheelRevolutionDataPresent = true;
                offset += 6;
                Log.d(TAG, "Received wheelRevolutionData");
            }
            if ((flags & 0x02) != 0) {
                cumulativeCrankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1+offset);
                lastCrankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 3+offset);
                crankRevolutionDataPresent = true;
                Log.d(TAG, "Received crankRevolutionData");
            }
            boolean needToPostData = _csc.onNewValues(cumulativeWheelRevolutions, lastWheelEventTime, cumulativeCrankRevolutions, lastCrankEventTime);

            res = String.format("Received cadence: %d, wheelRpm: %d %s", (int) _csc.getCrankRpm(), (int) _csc.getWheelRpm(), needToPostData ? "[NEW]" : "");

            if (needToPostData && crankRevolutionDataPresent) {
                BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
                sensorData.setCyclingCadence((int) _csc.getCrankRpm());
                _bus.post(sensorData);
            }
            if (needToPostData && wheelRevolutionDataPresent) {
                BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
                sensorData.setCyclingWheelRpm(_csc.getWheelRpm());
                _bus.post(sensorData);
            }
        } else if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {
            final int battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            deviceBattery.put(gatt.getDevice().getAddress(), battery);
            postLightState(gatt);
            try { if (gatt.getService(UUID_GOPRO_SERVICE) != null) postGoProState(gatt); } catch (Exception e) {}
            res = String.format("Received battery: %d", battery);
        } else if (UUID_TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            Log.d(TAG, String.format("flags: %d|%s", flags, Integer.toBinaryString(flags)));
            /*byte[] values = characteristic.getValue();
            String tmp = "";
            for(int i=0; i<values.length; i++) {
                tmp += String.format("|%d(%02X)", values[i], values[i]);
            }
            Log.d(TAG, "characteristic Temperature=" + tmp);*/
            int offset;
            String units;
            if ((flags & 0x00) == 0) {
                units = "Celsius";
                offset = 1;
            } else {
                units = "Fahrenheit";
                offset = 2;
            }
            float temperature = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, offset);
            res = String.format("Received temperature: %f %s", temperature, units);
            if (offset == 2) {
                // force conversion to celsius
                temperature = ((temperature - 32) * 5) / 9;
            }
            BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
            sensorData.setTemperature(temperature);
            _bus.post(sensorData);
        } else if (UUID_RSC_MEASUREMENT.equals(characteristic.getUuid())) {
            int speed = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
            int cadence = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3);

            res = String.format("Received running speeed: %d m/s, running cadence: %d", speed, cadence);
            BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
            sensorData.setRunningCadence((int) cadence);
            _bus.post(sensorData);

        } else if (UUID_CYCLING_POWER_MEASUREMENT.equals(characteristic.getUuid())) {
            int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            int offset = 2;
            // Instantaneous Power is mandatory and sits immediately after the Flags field
            final int instantaneousPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
            offset += 2;
            // bit 0: Pedal Power Balance Present (uint8)
            if ((flags & 0x01) != 0) offset += 1;
            // bit 1: Pedal Power Balance Reference (no field)
            // bit 2: Accumulated Torque Present (uint16)
            if ((flags & 0x04) != 0) offset += 2;
            // bit 3: Accumulated Torque Source (no field)
            // bit 4: Wheel Revolution Data Present (uint32 + uint16)
            boolean wheelPresent = (flags & 0x10) != 0;
            if (wheelPresent) offset += 6;
            // bit 5: Crank Revolution Data Present (uint16 + uint16)
            boolean crankPresent = (flags & 0x20) != 0;
            int cumulativeCrankRevolutions = 0;
            int lastCrankEventTime = 0;
            if (crankPresent) {
                cumulativeCrankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                lastCrankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset + 2);
                offset += 4;
            }
            // bit 6: Extreme Force Magnitudes Present (sint16 + sint16)
            if ((flags & 0x40) != 0) offset += 4;
            // bit 7: Extreme Torque Magnitudes Present (sint16 + sint16)
            if ((flags & 0x80) != 0) offset += 4;
            // bit 8: Extreme Angles Present (uint12 + uint12, 3 bytes)
            if ((flags & 0x100) != 0) offset += 3;
            // bit 9: Top Dead Spot Angle Present (uint8)
            if ((flags & 0x200) != 0) offset += 1;
            // bit 10: Bottom Dead Spot Angle Present (uint8)
            if ((flags & 0x400) != 0) offset += 1;
            // bit 11: Accumulated Energy Present (uint16)
            if ((flags & 0x800) != 0) offset += 2;
            // bit 12: Offset Compensation Indicator (no field); bits 13-15 reserved

            BleSensorData powerData = new BleSensorData(gatt.getDevice().getAddress());
            powerData.setPower(instantaneousPower);
            _bus.post(powerData);

            if (crankPresent) {
                if (_cpsLastCrankEventTime != 0 && cumulativeCrankRevolutions >= _cpsCrankRevolutions) {
                    int dRev = cumulativeCrankRevolutions - _cpsCrankRevolutions;
                    int dTime = lastCrankEventTime - (int) _cpsLastCrankEventTime; // in 1/1024 s
                    if (dTime > 0) {
                        int rpm = (int) Math.round(dRev * 60.0 * 1024.0 / dTime);
                        BleSensorData cadenceData = new BleSensorData(gatt.getDevice().getAddress());
                        cadenceData.setCyclingCadence(rpm);
                        _bus.post(cadenceData);
                    }
                }
                _cpsCrankRevolutions = cumulativeCrankRevolutions;
                _cpsLastCrankEventTime = lastCrankEventTime;
            }
            res = String.format("Received power: %d", instantaneousPower);

        } else if (UUID_INDOOR_BIKE_DATA.equals(characteristic.getUuid())) {
            // FTMS Indoor Bike Data - 0x2AD2
            // Flags (uint16) followed by optional fields based on flags
            // Bit 0: Instantaneous Speed (uint16, 0.01 km/h)
            // Bit 1: Average Speed (uint16, 0.01 km/h)
            // Bit 2: Instantaneous Cadence (uint16, 0.5 rpm)
            // Bit 3: Average Cadence (uint16, 0.5 rpm)
            // Bit 4: Total Distance (uint32, m)
            // Bit 5: Instantaneous Power (sint16, watts)
            // Bit 6: Average Power (sint16, watts)
            // Bit 7: Instantaneous Resistance Level (sint16)
            // Bit 8: Average Resistance Level (sint16)
            // Bit 9: Instantaneous Heart Rate (uint8, bpm)
            // Bit 10: Average Heart Rate (uint8, bpm)
            // Bit 11: Instantaneous METs (uint8)
            // Bit 12: Elapsed Time (uint16, s)
            // Bit 13: Remaining Time (uint16, s)
            // Bits 14-15: Reserved
            byte[] data = characteristic.getValue();
            if (data != null && data.length >= 2) {
                int flags = data[0] & 0xFF | (data[1] & 0xFF) << 8;
                int offset = 2;
                int instSpeed = 0, instCadence = 0, instPower = 0, resistance = 0, targetPower = 0;
                if ((flags & 0x01) != 0) { // Instantaneous Speed
                    if (offset + 1 < data.length) {
                        instSpeed = data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
                    }
                    offset += 2;
                }
                if ((flags & 0x02) != 0) offset += 2; // Average Speed
                if ((flags & 0x04) != 0) { // Instantaneous Cadence
                    if (offset + 1 < data.length) {
                        instCadence = data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
                    }
                    offset += 2;
                }
                if ((flags & 0x08) != 0) offset += 2; // Average Cadence
                if ((flags & 0x10) != 0) offset += 4; // Total Distance
                if ((flags & 0x20) != 0) { // Instantaneous Power
                    if (offset + 1 < data.length) {
                        instPower = (short)(data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8);
                    }
                    offset += 2;
                }
                if ((flags & 0x40) != 0) offset += 2; // Average Power
                if ((flags & 0x80) != 0) { // Instantaneous Resistance
                    if (offset + 1 < data.length) {
                        resistance = (short)(data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8);
                    }
                    offset += 2;
                }
                if ((flags & 0x100) != 0) offset += 2; // Average Resistance
                if ((flags & 0x200) != 0) offset += 1; // Instantaneous HR
                if ((flags & 0x400) != 0) offset += 1; // Average HR
                if ((flags & 0x800) != 0) offset += 1; // METs
                if ((flags & 0x1000) != 0) offset += 2; // Elapsed Time
                if ((flags & 0x2000) != 0) offset += 2; // Remaining Time
                // Target Power is not in standard Indoor Bike Data - may come from Training Status or Control Point
                BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
                sensorData.setFtmsIndoorBikeData(instSpeed, instCadence, instPower, resistance, targetPower);
                _bus.post(sensorData);
                res = String.format("FTMS Indoor Bike: speed=%d cad=%d power=%d resistance=%d", instSpeed, instCadence, instPower, resistance);
            }
        } else if (UUID_FITNESS_MACHINE_STATUS.equals(characteristic.getUuid())) {
            // Fitness Machine Status - 0x2ADA (uint16)
            int status = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
            sensorData.setFtmsStatus(status);
            _bus.post(sensorData);
            res = String.format("FTMS Status: %d", status);
        } else if (UUID_TRAINING_STATUS.equals(characteristic.getUuid())) {
            // Training Status - 0x2AD3 (uint8)
            int status = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
            sensorData.setFtmsTrainingStatus(status);
            _bus.post(sensorData);
            res = String.format("FTMS Training Status: %d", status);
        } else if (UUID_SUPPORTED_RESISTANCE_LEVEL_RANGE.equals(characteristic.getUuid())) {
            // Supported Resistance Level Range - 0x2AD5 (uint16 min, uint16 max)
            if (characteristic.getValue() != null && characteristic.getValue().length >= 4) {
                int min = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                int max = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2);
                String addr = gatt.getDevice().getAddress();
                trainerMinResistance.put(addr, min);
                trainerMaxResistance.put(addr, max);
                Log.d(TAG, String.format("FTMS Resistance Range: min=%d max=%d for %s", min, max, addr));
            }
            // Read other ranges if not already read
            readSupportedRanges(gatt);
            res = "FTMS Resistance Range read";
        } else if (UUID_SUPPORTED_POWER_RANGE.equals(characteristic.getUuid())) {
            // Supported Power Range - 0x2AD6 (uint16 min, uint16 max)
            if (characteristic.getValue() != null && characteristic.getValue().length >= 4) {
                int min = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                int max = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2);
                String addr = gatt.getDevice().getAddress();
                trainerMinPower.put(addr, min);
                trainerMaxPower.put(addr, max);
                Log.d(TAG, String.format("FTMS Power Range: min=%d max=%d for %s", min, max, addr));
            }
            res = "FTMS Power Range read";
        } else if (UUID_SUPPORTED_SPEED_RANGE.equals(characteristic.getUuid())) {
            // Supported Speed Range - 0x2AD8 (uint16 min, uint16 max) in 0.01 km/h
            if (characteristic.getValue() != null && characteristic.getValue().length >= 4) {
                int min = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                int max = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2);
                String addr = gatt.getDevice().getAddress();
                trainerMinSpeed.put(addr, min);
                trainerMaxSpeed.put(addr, max);
                Log.d(TAG, String.format("FTMS Speed Range: min=%d max=%d for %s", min, max, addr));
            }
            res = "FTMS Speed Range read";
        } else if (UUID_FITNESS_MACHINE_CONTROL_POINT.equals(characteristic.getUuid())) {
            // Control Point Response - 0x2AD9
            byte[] data = characteristic.getValue();
            if (data != null && data.length >= 3) {
                int requestOpcode = data[1] & 0xFF;
                int responseCode = data[2] & 0xFF;
                // Response codes: 1=Success, 2=Not Supported, 3=Invalid Parameter, 4=Operation Failed, 5=Control Not Permitted
                String addr = gatt.getDevice().getAddress();
                if (requestOpcode == 0x00 && responseCode == 1) { // Request Control success
                    trainerHasControl.put(addr, true);
                    Log.d(TAG, "FTMS Control granted for " + addr);
                    // Post updated state
                    BleSensorData sensorData = new BleSensorData(addr);
                    sensorData.setHasControl(true);
                    _bus.post(sensorData);
                } else if (responseCode != 1) {
                    Log.w(TAG, String.format("FTMS Control Point error: opcode=0x%02X response=%d", requestOpcode, responseCode));
                }
            }
            res = "FTMS Control Point response";
        } else if (UUID.fromString("00002a65-0000-1000-8000-00805f9b34fb").equals(characteristic.getUuid())) {
            // CPS Feature (0x2A65) - read for power range info
            // For Wahoo KICKR, use default range 0-2000W
            String addr = gatt.getDevice().getAddress();
            wahooMinPower.put(addr, 0);
            wahooMaxPower.put(addr, 2000);
            Log.d(TAG, "CPS Feature read for " + addr + ", default power range 0-2000W");
            // Post updated trainer state with ranges
            postTrainerState(gatt, true, true);
            res = "CPS Feature read";
        } else if (UUID_WAHOO_CYCLING_POWER_EXTENSION.equals(characteristic.getUuid())) {
            // Wahoo Extension indication (command response)
            byte[] data = characteristic.getValue();
            if (data != null && data.length >= 2) {
                int echoOpcode = data[0] & 0xFF;
                int result = data[1] & 0xFF;
                String addr = gatt.getDevice().getAddress();
                if (result == 0) {
                    Log.d(TAG, "Wahoo command 0x" + Integer.toHexString(echoOpcode) + " confirmed");
                    // Update TrainerInfo with confirmed value
                    if (echoOpcode == 0x42) { // Set ERG Power
                        int confirmedPower = 0;
                        if (data.length >= 4) {
                            confirmedPower = (data[2] & 0xFF) | ((data[3] & 0xFF) << 8);
                        }
                        postTrainerPowerConfirmed(addr, confirmedPower);
                    } else if (echoOpcode == 0x41) { // Set Level
                        int confirmedLevel = data.length >= 3 ? (data[2] & 0xFF) : 0;
                        postTrainerLevelConfirmed(addr, confirmedLevel);
                    }
                } else {
                    Log.w(TAG, "Wahoo command 0x" + Integer.toHexString(echoOpcode) + " failed: " + result);
                }
            }
            res = "Wahoo Extension indication";
        } else if (UUID_MODEL_NUMBER.equals(characteristic.getUuid())) {
            String model = characteristic.getStringValue(0);
            if (model != null) {
                model = model.trim();
                deviceModels.put(gatt.getDevice().getAddress(), model);
                Log.d(TAG, "Model Number: " + model + " for " + gatt.getDevice().getAddress());
                String low = model.toLowerCase();
                String type = "unknown";
                if (low.contains("flare")) type = "rear";
                else if (low.contains("ion") || low.contains("circuit")) type = "front";
                Log.d(TAG, "Light type inferred as " + type + " from model " + model);
                postLightState(gatt);
                try { if (gatt.getService(UUID_GOPRO_SERVICE) != null) postGoProState(gatt); } catch (Exception e) {}
            }
        } else if (UUID_GOPRO_COMMAND.equals(characteristic.getUuid()) || UUID_GOPRO_RESPONSE.equals(characteristic.getUuid()) || UUID_GOPRO_QUERY.equals(characteristic.getUuid())) {
            byte[] data = characteristic.getValue();
            Log.d(TAG, "GoPro response: " + (data != null ? java.util.Arrays.toString(data) : "null"));
            postGoProState(gatt);
        } else if (UUID_LIGHT_MODE.equals(characteristic.getUuid())) {
            int lm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            Log.d(TAG, String.format("recieved mode %d",lm));
            light_mode.put(gatt, lm);
            postLightState(gatt);
            BleSensorData sensorData = new BleSensorData(gatt.getDevice().getAddress());
            _bus.post(sensorData);
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data) {
                    stringBuilder.append(String.format("%02X ", byteChar));
                }
                //intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
                // TODO(jay) post something?
                res = "Received data:" + display(characteristic) + "=>" + stringBuilder.toString();
            }
        }
        return res;
    }

    private void displayGattServices(BluetoothGatt gatt) {
        Log.d(TAG, "displayGattServices");
        List<BluetoothGattService> gattServices = gatt.getServices();
        allwrites = false;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            if (debug) Log.i(TAG, display(gatt) + " displayGattServices gattService: " + display(gattService));

            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                int charaProp = gattCharacteristic.getProperties();
                if (debug) Log.i(TAG, display(gatt) + " displayGattServices characteristic: " +  display(gattCharacteristic) + " charaProp=" + charaProp);
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                    if (gattCharacteristic.getUuid().toString().equals("00002a00-0000-1000-8000-00805f9b34fb") // device name
//                            || gattCharacteristic.getUuid().toString().equals("00002a38-0000-1000-8000-00805f9b34fb") // Body Sensor Location
//                     ) {
                        //readCharacteristic(gattCharacteristic);
                    //}
                }
                if (
                        UUID_HEART_RATE_MEASUREMENT.equals(gattCharacteristic.getUuid())
                        || UUID_CSC_MEASUREMENT.equals(gattCharacteristic.getUuid())
                        || UUID_RSC_MEASUREMENT.equals(gattCharacteristic.getUuid())
                        || UUID_BATTERY_LEVEL.equals(gattCharacteristic.getUuid())
                        || UUID_TEMPERATURE_MEASUREMENT.equals(gattCharacteristic.getUuid())
                        || UUID_CYCLING_POWER_MEASUREMENT.equals(gattCharacteristic.getUuid())
                        || UUID_LIGHT_MODE.equals(gattCharacteristic.getUuid())
                        || UUID_GOPRO_COMMAND.equals(gattCharacteristic.getUuid())
                        || UUID_GOPRO_RESPONSE.equals(gattCharacteristic.getUuid())
                        || UUID_GOPRO_QUERY.equals(gattCharacteristic.getUuid())

                ) {
                    if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        setCharacteristicNotification(gatt, gattCharacteristic, true);
                    }
                }
                if (UUID_MODEL_NUMBER.equals(gattCharacteristic.getUuid()) && (charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    readCharacteristicQueue.add(new PendingCharacteristicWrite(gatt, gattCharacteristic));
                }
                // FTMS Indoor Bike Data - enable notifications
                if (UUID_INDOOR_BIKE_DATA.equals(gattCharacteristic.getUuid()) && (charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    setCharacteristicNotification(gatt, gattCharacteristic, true);
                }
                // FTMS Fitness Machine Status - enable notifications
                if (UUID_FITNESS_MACHINE_STATUS.equals(gattCharacteristic.getUuid()) && (charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    setCharacteristicNotification(gatt, gattCharacteristic, true);
                }
                // FTMS Training Status - enable notifications
                if (UUID_TRAINING_STATUS.equals(gattCharacteristic.getUuid()) && (charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    setCharacteristicNotification(gatt, gattCharacteristic, true);
                }
                // FTMS Control Point - enable notifications (for responses)
                if (UUID_FITNESS_MACHINE_CONTROL_POINT.equals(gattCharacteristic.getUuid()) && (charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    setCharacteristicNotification(gatt, gattCharacteristic, true);
                }
                // FTMS Supported Ranges - read them
                if ((UUID_SUPPORTED_RESISTANCE_LEVEL_RANGE.equals(gattCharacteristic.getUuid())
                        || UUID_SUPPORTED_POWER_RANGE.equals(gattCharacteristic.getUuid())
                        || UUID_SUPPORTED_SPEED_RANGE.equals(gattCharacteristic.getUuid()))
                        && (charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    readCharacteristicQueue.add(new PendingCharacteristicWrite(gatt, gattCharacteristic));
                }
                // Wahoo proprietary Cycling Power Extension - cache for control (pre-FTMS KICKR)
                if (UUID_WAHOO_CYCLING_POWER_EXTENSION.equals(gattCharacteristic.getUuid()) && (charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    String addr = gatt.getDevice().getAddress();
                    wahooExtensionChar.put(addr, gattCharacteristic);
                    // Enable indications for command responses
                    if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                        setCharacteristicNotification(gatt, gattCharacteristic, true);
                    }
                }
            }
        }
        if (!readCharacteristicQueue.isEmpty() && descriptorWriteQueue.isEmpty() && characteristicWriteQueue.isEmpty()) {
            try { PendingCharacteristicWrite r = readCharacteristicQueue.peek(); r.gatt.readCharacteristic(r.characteristic); } catch (Exception e) { Log.w(TAG, "read model failed "+e); }
        }
        try { postLightState(gatt); } catch (Exception e) {}
        // FTMS: if this is a fitness machine, read supported ranges and request control
        if (gatt.getService(UUID_FITNESS_MACHINE_SERVICE) != null) {
            String addr = gatt.getDevice().getAddress();
            trainerAddress = addr;
            // Auto-request control if not already requested
            if (!trainerControlRequested.getOrDefault(addr, false)) {
                trainerControlRequested.put(addr, true);
                requestControl(gatt);
            }
        }
        // Wahoo proprietary (pre-FTMS KICKR v2/v3) fallback
        if (gatt.getService(UUID_FITNESS_MACHINE_SERVICE) == null
                && gatt.getService(UUID_CYCLING_POWER_SERVICE) != null
                && wahooExtensionChar.containsKey(gatt.getDevice().getAddress())) {
            String addr = gatt.getDevice().getAddress();
            String deviceName = gatt.getDevice().getName();
            String modelName = deviceModels.get(addr);
            boolean isKickr = (deviceName != null && deviceName.toLowerCase().contains("kickr"))
                    || (modelName != null && modelName.toLowerCase().contains("kickr"));
            if (isKickr) {
                trainerAddress = addr;
                // Read CPS Feature for power range
                readCPSFeatureForRange(gatt);
                // Post initial trainer state with proprietary control capability
                postTrainerState(gatt, true, true);
            }
        }
        try { if (gatt.getService(UUID_GOPRO_SERVICE) != null) postGoProState(gatt); } catch (Exception e) {}
        try {
            if (gatt.getService(UUID_GOPRO_SERVICE) != null) {
                String addr = gatt.getDevice().getAddress();
                Boolean pending = goproPending.get(addr);
                if (pending != null) {
                    setGoProRecording(gatt, pending);
                    goproPending.remove(addr);
                    postGoProState(gatt);
                }
            }
        } catch (Exception e) {}
        start_stop_handler(gatt, true);
        allwrites = true;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "readCharacteristic not supported" + display(characteristic));
//        Log.d(TAG, "readCharacteristic " + display(characteristic));
//        if (mBluetoothAdapter == null /*|| mBluetoothGatt == null*/) {
//            Log.w(TAG, "BluetoothAdapter not initialized");
//            return;
//        }
//        //if (MainActivity.debug) Log.d(TAG, "readCharacteristic "+characteristic.getUuid().toString());
//        //mBluetoothGatt.readCharacteristic(characteristic);
//
//        //put the characteristic into the read queue
//        readCharacteristicQueue.add(characteristic);
//        //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
//        //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
//        if((readCharacteristicQueue.size() == 1) && (descriptorWriteQueue.size() == 0) && allwrites)
//            mBluetoothGatt.readCharacteristic(characteristic);
    }
    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    private void setCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        Log.d(TAG, display(gatt) + " setCharacteristicNotification " + display(characteristic));
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        //if (debug) Log.w(TAG, "setCharacteristicNotification");
        gatt.setCharacteristicNotification(characteristic, enabled);
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        // This is specific to Heart Rate Measurement.
        /*if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())
            || UUID_CSC_MEASUREMENT.equals(characteristic.getUuid())
            || UUID_RSC_MEASUREMENT.equals(characteristic.getUuid())
        ) {*/
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(BLESampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            writeGattDescriptor(gatt, descriptor);
        /*} else {
            if (debug) Log.i(TAG, "unused characteristics2:" + display(gatt, characteristic));
        }*/
    }

    public void writeGattDescriptor(BluetoothGatt gatt, BluetoothGattDescriptor d){
        descriptorWriteQueue.add(new PendingDescriptorWrite(gatt, d));
        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
//        if(descriptorWriteQueue.size() == 1) {
//            gatt.writeDescriptor(d);
//        }
    }

    public String display(BluetoothGattService gattService) {
        return " s:"+gattService.getUuid().toString().substring(4, 8) + " " + BLESampleGattAttributes.lookup(gattService.getUuid().toString(), "UNK");
    }
    public String display(BluetoothGattCharacteristic characteristic) {
        return " c:"+characteristic.getUuid().toString().substring(4, 8) + " " + BLESampleGattAttributes.lookup(characteristic.getUuid().toString(), "UNK");
    }
    public String display(BluetoothGatt gatt) {
        return " @:"+gatt.getDevice().getAddress().toString().substring(0, 5);
    }
    public String display(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        return display(gatt) + display(characteristic);
    }


    @Override
    public void handleTimeout() {
        // timeout TIMEOUT_CONNECTGATT ms after last attempt to connectGatt
        // force cancel of pending connections

        if (mGattsConnectionPending.size() == 0) {
            // no pending connections, nothing to do
            return;
        }
        Log.d(TAG, "handleTimeout mGattsConnectionPending.size:" + mGattsConnectionPending.size());
        Iterator<Map.Entry<String, BluetoothGatt>> iterator = mGattsConnectionPending.entrySet().iterator();
        while (iterator.hasNext()) {
            BluetoothGatt gatt = iterator.next().getValue();
            Log.d(TAG, "handleTimeout close pending connection to " + display(gatt));
            gatt.close();
            mGattsConnectionPending.remove(gatt.getDevice().getAddress());
            reconnectLater(gatt);
        }
    }

    public void start_stop_handler(BluetoothGatt gatt, Boolean status) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        if (gatt.getService(UUID_LIGHT_MODE_SERVICE) != null && prefs.getBoolean(Constants.PREF_AUTOSTART_LIGHTS, true)) setLightMode(gatt, status);
        if (gatt.getService(UUID_GOPRO_SERVICE) != null && prefs.getBoolean(Constants.PREF_AUTOSTART_GOPRO, true)) setGoProRecording(gatt, status);
        Log.d(TAG, "descriptorWriteQueue.size=" + descriptorWriteQueue.size());
        Log.d(TAG, "characteristicWriteQueue.size=" + characteristicWriteQueue.size());
        if (!characteristicWriteQueue.isEmpty()) {
            PendingCharacteristicWrite w = characteristicWriteQueue.peek();
            w.gatt.writeCharacteristic(w.characteristic);
        } else if (!descriptorWriteQueue.isEmpty()) {
            PendingDescriptorWrite d = descriptorWriteQueue.peek();
            d.gatt.writeDescriptor(d.descriptor);
        }
    }

    public BluetoothGattCharacteristic getCharacter(BluetoothGatt gatt, UUID service, UUID characteristic, String logString) {
        final String device = gatt.getDevice().getName().toString();
        final BluetoothGattService gattService = gatt.getService(service);
        if (gattService == null) {
            Log.d(TAG, logString + " service not found for " + device);
        } else {
            final BluetoothGattCharacteristic gattChar = gattService.getCharacteristic(characteristic);
            if (gattChar == null) {
                Log.d(TAG, logString + " characteristic not found for " + device);
            } else {
                return gattChar;
            }
        }
        return null;
    }

    public void setGoProRecording(BluetoothGatt gatt, Boolean gopro_on) {
        if (gatt == null || gatt.getService(UUID_GOPRO_SERVICE) == null) return;
        byte[] newMode = new byte[] { 0x03, 0x01, 0x01, 0x00 };
        if (gopro_on) {
            newMode = new byte[] { 0x03, 0x01, 0x01, 0x01 };
        }
        final BluetoothGattCharacteristic gattChar = getCharacter(gatt, UUID_GOPRO_SERVICE, UUID_GOPRO_COMMAND, "GOPRO");
        if (gattChar != null) {
            Log.i(TAG, "Setting GoPro "+gopro_on+" for "+gatt.getDevice().getAddress());
            gattChar.setValue(newMode);
            characteristicWriteQueue.add(new PendingCharacteristicWrite(gatt, gattChar));
            goproRecording.put(gatt.getDevice().getAddress(), gopro_on);
            if (!goproMode.containsKey(gatt.getDevice().getAddress())) goproMode.put(gatt.getDevice().getAddress(), "Video");
            postGoProState(gatt);
        }
    }

    // FTMS Trainer Control Methods
    private void readSupportedRanges(BluetoothGatt gatt) {
        String addr = gatt.getDevice().getAddress();
        BluetoothGattService ftmsService = gatt.getService(UUID_FITNESS_MACHINE_SERVICE);
        if (ftmsService == null) return;
        // Read resistance range if not already read
        if (!trainerMinResistance.containsKey(addr)) {
            BluetoothGattCharacteristic chr = ftmsService.getCharacteristic(UUID_SUPPORTED_RESISTANCE_LEVEL_RANGE);
            if (chr != null) readCharacteristicQueue.add(new PendingCharacteristicWrite(gatt, chr));
        }
        // Read power range if not already read
        if (!trainerMinPower.containsKey(addr)) {
            BluetoothGattCharacteristic chr = ftmsService.getCharacteristic(UUID_SUPPORTED_POWER_RANGE);
            if (chr != null) readCharacteristicQueue.add(new PendingCharacteristicWrite(gatt, chr));
        }
        // Read speed range if not already read
        if (!trainerMinSpeed.containsKey(addr)) {
            BluetoothGattCharacteristic chr = ftmsService.getCharacteristic(UUID_SUPPORTED_SPEED_RANGE);
            if (chr != null) readCharacteristicQueue.add(new PendingCharacteristicWrite(gatt, chr));
        }
    }

    public void requestControl(String address) {
        BluetoothGatt gatt = mGatts.get(address);
        if (gatt == null) gatt = mGattsConnectionPending.get(address);
        if (gatt != null) requestControl(gatt);
    }

    private void requestControl(BluetoothGatt gatt) {
        writeControlPoint(gatt, new byte[] { 0x00 }); // OpCode 0x00: Request Control
    }

    public void setTargetPower(String address, int watts) {
        BluetoothGatt gatt = mGatts.get(address);
        if (gatt == null) gatt = mGattsConnectionPending.get(address);
        if (gatt != null) setTargetPower(gatt, watts);
    }

    private void setTargetPower(BluetoothGatt gatt, int watts) {
        // OpCode 0x02: Set Target Power (uint16, watts)
        byte[] data = new byte[] { 0x02, (byte)(watts & 0xFF), (byte)((watts >> 8) & 0xFF) };
        writeControlPoint(gatt, data);
    }

    public void setResistanceLevel(String address, int level) {
        BluetoothGatt gatt = mGatts.get(address);
        if (gatt == null) gatt = mGattsConnectionPending.get(address);
        if (gatt != null) setResistanceLevel(gatt, level);
    }

    private void setResistanceLevel(BluetoothGatt gatt, int level) {
        // OpCode 0x03: Set Resistance Level (sint16)
        byte[] data = new byte[] { 0x03, (byte)(level & 0xFF), (byte)((level >> 8) & 0xFF) };
        writeControlPoint(gatt, data);
    }

    private void writeControlPoint(BluetoothGatt gatt, byte[] data) {
        BluetoothGattService ftmsService = gatt.getService(UUID_FITNESS_MACHINE_SERVICE);
        if (ftmsService == null) {
            Log.w(TAG, "FTMS service not found for control point write");
            return;
        }
        BluetoothGattCharacteristic chr = ftmsService.getCharacteristic(UUID_FITNESS_MACHINE_CONTROL_POINT);
        if (chr == null) {
            Log.w(TAG, "FTMS Control Point characteristic not found");
            return;
        }
        chr.setValue(data);
        characteristicWriteQueue.add(new PendingCharacteristicWrite(gatt, chr));
        triggerNextWrite();
    }

    // Called from DashboardViewModel via bus event
    public void handleTrainerControlRequest(TrainerControlRequest req) {
        String addr = req.getAddress();
        BluetoothGatt gatt = mGatts.get(addr);
        if (gatt == null) gatt = mGattsConnectionPending.get(addr);
        if (gatt == null) return;
        if (req.isRequestControl()) {
            requestControl(gatt);
        } else if (req.isErgMode()) {
            if (req.getTargetPower() > 0) {
                setTargetPower(gatt, req.getTargetPower());
            }
        } else {
            if (req.getResistanceLevel() > 0) {
                setResistanceLevel(gatt, req.getResistanceLevel());
            }
        }
    }

    // Wahoo proprietary (pre-FTMS KICKR) control methods
    public void handleWahooTrainerControlRequest(WahooTrainerControlRequest req) {
        String addr = req.getAddress();
        BluetoothGatt gatt = mGatts.get(addr);
        if (gatt == null) gatt = mGattsConnectionPending.get(addr);
        if (gatt == null) return;
        if (req.isErgMode()) {
            // Always send the target power so ERG mode is entered even at 0 W
            setWahooTargetPower(gatt, req.getTargetPower());
        } else {
            if (req.getResistanceLevel() > 0) {
                setWahooResistanceLevel(gatt, req.getResistanceLevel());
            }
        }
    }

    private void readCPSFeatureForRange(BluetoothGatt gatt) {
        BluetoothGattService cps = gatt.getService(UUID_CYCLING_POWER_SERVICE);
        if (cps != null) {
            // CPS Feature characteristic UUID: 00002a65-0000-1000-8000-00805f9b34fb
            BluetoothGattCharacteristic feature = cps.getCharacteristic(UUID.fromString("00002a65-0000-1000-8000-00805f9b34fb"));
            if (feature != null && (feature.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                readCharacteristicQueue.add(new PendingCharacteristicWrite(gatt, feature));
            }
        }
    }

    private void postTrainerState(BluetoothGatt gatt, boolean connected, boolean isWahooProprietary) {
        String addr = gatt.getDevice().getAddress();
        String name = "";
        try { name = gatt.getDevice().getName(); } catch (Exception e) {}
        if (name == null) name = addr;
        String model = deviceModels.get(addr);
        if (model == null) model = name;
        int minPower = wahooMinPower.getOrDefault(addr, 0);
        int maxPower = wahooMaxPower.getOrDefault(addr, 2000);
        BleSensorData sensorData = new BleSensorData(addr);
        // Set individual fields FIRST, then combined methods in correct order
        // so event type ends up as SENSOR_FTMS_INDOOR_BIKE (7)
        sensorData.setMinPower(minPower);
        sensorData.setMaxPower(maxPower);
        sensorData.setMinResistance(0);
        sensorData.setMaxResistance(100);
        sensorData.setHasControl(true);
        // These must be called in order: ranges first, then indoor bike data LAST
        sensorData.setFtmsSupportedRanges(0, 100, minPower, maxPower, 0, 100);
        sensorData.setFtmsIndoorBikeData(0, 0, 0, 0, 0);
        _bus.post(sensorData);
    }

    private void postTrainerPowerConfirmed(String addr, int watts) {
        BleSensorData sensorData = new BleSensorData(addr);
        sensorData.setFtmsIndoorBikeData(0, 0, watts, 0, watts);
        sensorData.setHasControl(true);
        _bus.post(sensorData);
    }

    private void postTrainerLevelConfirmed(String addr, int level) {
        BleSensorData sensorData = new BleSensorData(addr);
        sensorData.setFtmsIndoorBikeData(0, 0, 0, level, 0);
        sensorData.setHasControl(true);
        _bus.post(sensorData);
    }

    public void setWahooTargetPower(String address, int watts) {
        BluetoothGatt gatt = mGatts.get(address);
        if (gatt == null) gatt = mGattsConnectionPending.get(address);
        if (gatt != null) setWahooTargetPower(gatt, watts);
    }

    private void setWahooTargetPower(BluetoothGatt gatt, int watts) {
        BluetoothGattCharacteristic chr = wahooExtensionChar.get(gatt.getDevice().getAddress());
        if (chr != null) {
            // OpCode 0x42: Set ERG Power (uint16 LE, watts)
            byte[] data = new byte[] { 0x42, (byte)(watts & 0xFF), (byte)((watts >> 8) & 0xFF) };
            writeWahooExtension(gatt.getDevice().getAddress(), data);
        }
    }

    public void setWahooResistanceLevel(String address, int level) {
        BluetoothGatt gatt = mGatts.get(address);
        if (gatt == null) gatt = mGattsConnectionPending.get(address);
        if (gatt != null) setWahooResistanceLevel(gatt, level);
    }

    private void setWahooResistanceLevel(BluetoothGatt gatt, int level) {
        BluetoothGattCharacteristic chr = wahooExtensionChar.get(gatt.getDevice().getAddress());
        if (chr != null) {
            // OpCode 0x41: Set Level (1-9). Map 0-100 to 1-9.
            int kickrLevel = Math.max(1, Math.min(9, (level * 9) / 100 + 1));
            byte[] data = new byte[] { 0x41, (byte)kickrLevel };
            writeWahooExtension(gatt.getDevice().getAddress(), data);
        }
    }

    private void writeWahooExtension(String address, byte[] data) {
        BluetoothGattCharacteristic chr = wahooExtensionChar.get(address);
        if (chr != null) {
            BluetoothGatt gatt = mGatts.get(address);
            if (gatt == null) gatt = mGattsConnectionPending.get(address);
            if (gatt != null) {
                // Use a default write unless the characteristic only supports write-without-response
                int writeType = (chr.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                chr.setWriteType(writeType);
                chr.setValue(data);
                characteristicWriteQueue.add(new PendingCharacteristicWrite(gatt, chr));
                triggerNextWrite();
            }
        }
    }
}
