package com.njackson.utils;

import java.util.ArrayList;

public class SensorGraphReduce {

    private static final int GRAPH_SEGMENTS = 14;
    private static final long ROLLING_WINDOW_MS = 300000;

    private ArrayList<Long> _timestamps = new ArrayList<Long>();
    private ArrayList<Integer> _values = new ArrayList<Integer>();

    public SensorGraphReduce() {}

    public void addValue(int value, long elapsedTimeMs) {
        _values.add(value);
        _timestamps.add(elapsedTimeMs);
        purgeOldValues(elapsedTimeMs);
    }

    private void purgeOldValues(long currentTimeMs) {
        while (!_timestamps.isEmpty()) {
            long oldest = _timestamps.get(0);
            if (currentTimeMs - oldest > ROLLING_WINDOW_MS) {
                _values.remove(0);
                _timestamps.remove(0);
            } else {
                break;
            }
        }
    }

    public int[] getGraphData() {
        if (_values.isEmpty()) {
            return new int[GRAPH_SEGMENTS];
        }

        int[] graphData = new int[GRAPH_SEGMENTS];
        long windowMs = ROLLING_WINDOW_MS;
        long binWidth = windowMs / GRAPH_SEGMENTS;

        for (int i = 0; i < _values.size(); i++) {
            long ts = _timestamps.get(i);
            int binIndex = (int) (ts / binWidth);
            if (binIndex >= GRAPH_SEGMENTS) {
                binIndex = GRAPH_SEGMENTS - 1;
            }
            graphData[binIndex] += _values.get(i);
        }

        int[] counts = new int[GRAPH_SEGMENTS];
        for (int i = 0; i < _timestamps.size(); i++) {
            long ts = _timestamps.get(i);
            int binIndex = (int) (ts / binWidth);
            if (binIndex >= GRAPH_SEGMENTS) {
                binIndex = GRAPH_SEGMENTS - 1;
            }
            counts[binIndex]++;
        }

        for (int i = 0; i < GRAPH_SEGMENTS; i++) {
            if (counts[i] > 0) {
                graphData[i] = graphData[i] / counts[i];
            }
        }

        return graphData;
    }

    public void resetData() {
        _timestamps = new ArrayList<Long>();
        _values = new ArrayList<Integer>();
    }
}