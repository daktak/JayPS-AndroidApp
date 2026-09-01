package com.njackson.utils;

import java.util.ArrayList;

public class SensorGraphReduce {

    private ArrayList<Integer> _bins = new ArrayList<Integer>();
    private long _lastBinChange = -1;
    private int _binSizeMs;
    private int _countInBin = 0;

    public SensorGraphReduce() { this(21428); }
    public SensorGraphReduce(int binSizeMs) { _binSizeMs = binSizeMs; }

    public void setBinInterval(int v) { _binSizeMs = v; }
    public ArrayList<Integer> getCache() { return _bins; }
    public void setCache(ArrayList<Integer> v) { _bins = v; }

    public void addValue(int value, long elapsedTimeMs) {
        if (_lastBinChange == -1) {
            _bins.add(value);
            _lastBinChange = elapsedTimeMs;
            _countInBin = 1;
            return;
        }
        if (_lastBinChange + _binSizeMs > elapsedTimeMs) {
            _bins.set(_bins.size() - 1, (_bins.get(_bins.size() - 1) * _countInBin + value) / (_countInBin + 1));
            _countInBin++;
        } else {
            _countInBin = 1;
            _bins.add(value);
            _lastBinChange = elapsedTimeMs;
        }
    }

    public int[] getGraphData() {
        double binsPerBar = 14.0 / (double) _bins.size();
        int[] graphData = new int[14];
        double binCount = binsPerBar;
        if (binsPerBar > 1) { binsPerBar = 1; binCount = 0; }
        int currentBinCount = 0;
        int currentBinItems = 0;
        int lastBin = 0;
        for (int n = 0; n < _bins.size(); n++) {
            currentBinCount += _bins.get(n);
            currentBinItems++;
            binCount += binsPerBar;
            if ((int) binCount == lastBin + 1) {
                graphData[lastBin] = (currentBinCount / currentBinItems);
                lastBin++;
                currentBinCount = 0;
                currentBinItems = 0;
            }
        }
        return graphData;
    }

    public void resetData() {
        _bins = new ArrayList<Integer>();
        _lastBinChange = -1;
        _countInBin = 0;
    }
}
