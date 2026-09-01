package com.njackson.events.BleServiceCommand;

public class GoProState {
    private final String address;
    private final String name;
    private final String model;
    private final String modeName;
    private final boolean isRecording;
    private final int battery;
    private final boolean connected;

    public GoProState(String address, String name, String model, String modeName, boolean isRecording, int battery, boolean connected) {
        this.address = address;
        this.name = name;
        this.model = model;
        this.modeName = modeName;
        this.isRecording = isRecording;
        this.battery = battery;
        this.connected = connected;
    }
    public String getAddress() { return address; }
    public String getName() { return name; }
    public String getModel() { return model; }
    public String getModeName() { return modeName; }
    public boolean isRecording() { return isRecording; }
    public int getBattery() { return battery; }
    public boolean isConnected() { return connected; }
}
