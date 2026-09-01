package com.njackson.events.BleServiceCommand;

import java.util.Map;

public class LightState {
    private final String address;
    private final String name;
    private final String model;
    private final String type;
    private final int currentMode;
    private final String currentModeName;
    private final Map<String, Integer> availableModes;
    private final boolean connected;
    private final int battery;

    public LightState(String address, String name, String model, String type, int currentMode, String currentModeName, Map<String, Integer> availableModes, boolean connected, int battery) {
        this.address = address;
        this.name = name;
        this.model = model;
        this.type = type;
        this.currentMode = currentMode;
        this.currentModeName = currentModeName;
        this.availableModes = availableModes;
        this.connected = connected;
        this.battery = battery;
    }
    public String getAddress() { return address; }
    public String getName() { return name; }
    public String getModel() { return model; }
    public String getType() { return type; }
    public int getCurrentMode() { return currentMode; }
    public String getCurrentModeName() { return currentModeName; }
    public Map<String, Integer> getAvailableModes() { return availableModes; }
    public boolean isConnected() { return connected; }
    public int getBattery() { return battery; }
}
