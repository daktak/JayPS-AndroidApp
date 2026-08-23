package com.njackson.activities;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dsi.ant.plugins.antplus.pcc.MultiDeviceSearch;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceType;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.njackson.R;

import java.util.EnumSet;

public class AntScanActivity extends ListActivity {

    private static final String TAG = "PB-AntScanActivity";

    private DeviceListAdapter mDeviceListAdapter;
    private MultiDeviceSearch mSearch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDeviceListAdapter = new DeviceListAdapter();
        setListAdapter(mDeviceListAdapter);

        EnumSet<DeviceType> types = EnumSet.of(
                DeviceType.HEARTRATE,
                DeviceType.BIKE_SPD,
                DeviceType.BIKE_CADENCE,
                DeviceType.BIKE_POWER,
                DeviceType.STRIDE_SDM);

        try {
            mSearch = new MultiDeviceSearch(this, types, new MultiDeviceSearch.SearchCallbacks() {
                @Override
                public void onSearchStarted(MultiDeviceSearch.RssiSupport supportsRssi) {
                }

                @Override
                public void onDeviceFound(com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult device) {
                    mDeviceListAdapter.addDevice(device);
                }

                @Override
                public void onSearchStopped(RequestAccessResult reason) {
                    Log.d(TAG, "search stopped: " + reason);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, R.string.ant_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult device = mDeviceListAdapter.getDevice(position);
        if (device == null) return;
        Intent returnIntent = new Intent();
        returnIntent.putExtra("ant_name", device.getDeviceDisplayName());
        returnIntent.putExtra("ant_address", String.valueOf(device.getAntDeviceNumber()));
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSearch != null) {
            mSearch.close();
            mSearch = null;
        }
        mDeviceListAdapter.clear();
    }

    private class DeviceListAdapter extends ArrayAdapter<com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult> {
        public DeviceListAdapter() {
            super(AntScanActivity.this, R.layout.ble_listitem_device, R.id.ble_device_name);
        }

        public void addDevice(com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult device) {
            if (device == null) return;
            for (int i = 0; i < getCount(); i++) {
                com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult existing = getItem(i);
                if (existing != null
                        && existing.getAntDeviceNumber() == device.getAntDeviceNumber()
                        && existing.getAntDeviceType() == device.getAntDeviceType()) {
                    return;
                }
            }
            add(device);
        }

        public com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult getDevice(int position) {
            return getItem(position);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            View v = super.getView(position, view, parent);
            com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult device = getItem(position);
            TextView name = (TextView) v.findViewById(R.id.ble_device_name);
            TextView address = (TextView) v.findViewById(R.id.ble_device_address);
            if (device != null) {
                name.setText(device.getDeviceDisplayName());
                address.setText(String.valueOf(device.getAntDeviceNumber()));
            }
            return v;
        }
    }
}
