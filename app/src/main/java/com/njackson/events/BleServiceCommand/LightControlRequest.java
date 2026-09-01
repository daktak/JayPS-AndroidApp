package com.njackson.events.BleServiceCommand;

public class LightControlRequest {
    private final String address;
    private final String modeName;
    public LightControlRequest(String address, String modeName) { this.address = address; this.modeName = modeName; }
    public String getAddress() { return address; }
    public String getModeName() { return modeName; }
}
