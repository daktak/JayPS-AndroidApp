package com.njackson.events.BleServiceCommand;

public class GoProControlRequest {
    private final String address;
    private final boolean isStart;
    public GoProControlRequest(String address, boolean isStart) { this.address = address; this.isStart = isStart; }
    public String getAddress() { return address; }
    public boolean isStart() { return isStart; }
}
