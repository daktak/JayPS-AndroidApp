package com.njackson.activities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.njackson.R
import com.njackson.sensor.BLESampleGattAttributes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class HRMScanActivity : ComponentActivity() {

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mScanning by mutableStateOf(false)
    private val mDevices = mutableListOf<BluetoothDevice>()
    private var devicesState by mutableStateOf<List<BluetoothDevice>>(emptyList())
    private val deviceUuids = mutableMapOf<String, List<UUID>>()
    private val mHandler = Handler(Looper.getMainLooper())
    private val supportedUuids = setOf(
        BLESampleGattAttributes.HEART_RATE_SERVICE.lowercase(),
        "00001816-0000-1000-8000-00805f9b34fb",
        BLESampleGattAttributes.CYCLING_POWER_SERVICE.lowercase(),
        BLESampleGattAttributes.GOPRO_SERVICE.lowercase(),
        BLESampleGattAttributes.LIGHT_MODE_SERVICE.lowercase(),
        "00001814-0000-1000-8000-00805f9b34fb"
    )

    companion object {
        private const val TAG = "PB-HRMScanActivity"
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_BT_PERMISSIONS = 2
        private const val SCAN_PERIOD = 10000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.ble_error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent { Content() }
    }

    override fun onResume() {
        super.onResume()
        if (!ensureBluetoothPermissions()) return
        startScanFlow()
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        mDevices.clear()
        devicesState = emptyList()
    }

    private fun ensureBluetoothPermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.BLUETOOTH_SCAN)
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQUEST_BT_PERMISSIONS)
            return false
        }
        return true
    }

    private fun startScanFlow() {
        if (mBluetoothAdapter?.isEnabled == false) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
            return
        }
        mDevices.clear()
        deviceUuids.clear()
        devicesState = emptyList()
        try {
            @Suppress("DEPRECATION")
            val bonded = mBluetoothAdapter?.bondedDevices
            bonded?.forEach { dev ->
                val n = try { dev.name } catch (_: SecurityException) { null }
                if (n != null && isLightName(n)) {
                    if (mDevices.none { it.address == dev.address }) {
                        mDevices.add(dev)
                        deviceUuids[dev.address] = listOf(UUID.fromString(BLESampleGattAttributes.LIGHT_MODE_SERVICE))
                    }
                }
            }
            if (mDevices.isNotEmpty()) devicesState = mDevices.toList()
        } catch (_: SecurityException) {}
        scanLeDevice(true)
    }

    private fun isLightName(name: String?): Boolean {
        if (name == null) return false
        val l = name.lowercase()
        return "flare" in l || "ion" in l || "circuit" in l || "bontrager" in l || "trek" in l
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startScanFlow()
            else { Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show(); finish() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) { finish(); return }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun scanLeDevice(enable: Boolean) {
        try {
            if (enable) {
                mHandler.postDelayed({
                    mScanning = false
                    try { mBluetoothAdapter?.stopLeScan(mLeScanCallback) } catch (_: SecurityException) {}
                }, SCAN_PERIOD)
                mScanning = true
                mBluetoothAdapter?.startLeScan(mLeScanCallback)
            } else {
                mScanning = false
                mBluetoothAdapter?.stopLeScan(mLeScanCallback)
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isSupported(uuids: List<UUID>?): Boolean {
        if (uuids.isNullOrEmpty()) return false
        return uuids.any { it.toString().lowercase() in supportedUuids }
    }

    private fun isSupported(device: BluetoothDevice, uuids: List<UUID>?, scanRecord: ByteArray?): Boolean {
        if (isSupported(uuids)) return true
        val name = try { device.name } catch (_: SecurityException) { null }
        if (isLightName(name)) return true
        if (scanRecord != null) {
            val rec = String(scanRecord, Charsets.UTF_8).lowercase()
            if ("flare" in rec || "bontrager" in rec) return true
        }
        return false
    }

    private fun iconFor(uuids: List<UUID>?): androidx.compose.ui.graphics.vector.ImageVector {
        val set = uuids?.map { it.toString().lowercase() }?.toSet() ?: emptySet()
        return when {
            BLESampleGattAttributes.HEART_RATE_SERVICE.lowercase() in set -> Icons.Filled.Favorite
            BLESampleGattAttributes.CYCLING_POWER_SERVICE.lowercase() in set -> Icons.Filled.Bolt
            "00001816-0000-1000-8000-00805f9b34fb" in set || "00001814-0000-1000-8000-00805f9b34fb" in set -> Icons.Filled.PedalBike
            BLESampleGattAttributes.GOPRO_SERVICE.lowercase() in set -> Icons.Filled.Videocam
            BLESampleGattAttributes.LIGHT_MODE_SERVICE.lowercase() in set -> Icons.Filled.Lightbulb
            else -> Icons.Filled.Bluetooth
        }
    }

    private fun labelFor(uuids: List<UUID>?): String {
        val set = uuids?.map { it.toString().lowercase() }?.toSet() ?: emptySet()
        return when {
            BLESampleGattAttributes.HEART_RATE_SERVICE.lowercase() in set -> "Heart Rate"
            BLESampleGattAttributes.CYCLING_POWER_SERVICE.lowercase() in set -> "Power"
            "00001816-0000-1000-8000-00805f9b34fb" in set -> "Cadence"
            "00001814-0000-1000-8000-00805f9b34fb" in set -> "Running Cadence"
            BLESampleGattAttributes.GOPRO_SERVICE.lowercase() in set -> "GoPro"
            BLESampleGattAttributes.LIGHT_MODE_SERVICE.lowercase() in set -> "Lights"
            else -> "Sensor"
        }
    }

    private val mLeScanCallback = BluetoothAdapter.LeScanCallback { device, _, scanRecord ->
        val uuids = parseServiceUuids(scanRecord)
        if (!isSupported(device, uuids, scanRecord)) return@LeScanCallback
        runOnUiThread {
            if (mDevices.none { it.address == device.address }) {
                mDevices.add(device)
                // keep real uuids if FlareRT via name had empty uuids, inject light service for icon/label
                val stored = if (uuids.isEmpty() && isLightName(try { device.name } catch (_: SecurityException) { null })) listOf(UUID.fromString(BLESampleGattAttributes.LIGHT_MODE_SERVICE)) else uuids
                deviceUuids[device.address] = stored
                devicesState = mDevices.toList()
            }
        }
    }

    private fun parseServiceUuids(advertisedData: ByteArray?): List<UUID> {
        val uuids = mutableListOf<UUID>()
        if (advertisedData == null) return uuids
        var offset = 0
        while (offset < advertisedData.size - 2) {
            var len = advertisedData[offset++].toInt() and 0xFF
            if (len == 0) break
            val type = advertisedData[offset++].toInt() and 0xFF
            when (type) {
                0x02, 0x03 -> {
                    while (len > 1) {
                        val uuid16 = (advertisedData[offset++].toInt() and 0xFF) + ((advertisedData[offset++].toInt() and 0xFF) shl 8)
                        len -= 2
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)))
                    }
                }
                0x06, 0x07 -> {
                    while (len >= 16) {
                        try {
                            val buffer = ByteBuffer.wrap(advertisedData, offset, 16).order(ByteOrder.LITTLE_ENDIAN)
                            val msb = buffer.getLong()
                            val lsb = buffer.getLong()
                            uuids.add(UUID(lsb, msb))
                        } catch (_: IndexOutOfBoundsException) {} finally {
                            offset += 16; len -= 16
                        }
                    }
                }
                else -> offset += (len - 1)
            }
        }
        return uuids
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() {
        com.njackson.ui.theme.KaypsTheme {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { androidx.compose.material3.Text(getString(R.string.ble_scan), style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                        actions = {
                            if (mScanning) TextButton(onClick = { scanLeDevice(false) }) { Text(getString(R.string.ble_stop)) }
                            else TextButton(onClick = { mDevices.clear(); devicesState = emptyList(); scanLeDevice(true) }) { Text(getString(R.string.ble_scan)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
                    )
                }
            ) { pad ->
                Column(modifier = Modifier.fillMaxSize().padding(pad)) {
                    if (mScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (devicesState.isEmpty() && !mScanning) {
                            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No supported sensors found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("Scanning for Heart Rate, Cadence, Power, GoPro, Lights — tap Scan", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                                TextButton(onClick = { mDevices.clear(); devicesState = emptyList(); scanLeDevice(true) }) { Text(getString(R.string.ble_scan)) }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (devicesState.isEmpty() && mScanning) {
                                    item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(12.dp)); Text("Scanning for supported sensors…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                                }
                                items(devicesState) { device ->
                                    val parsed = try { null } catch (_: Exception) { null }
                                    DeviceCard(device)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DeviceCard(device: BluetoothDevice) {
        val uuids = deviceUuids[device.address]
        val icon = iconFor(uuids)
        val label = labelFor(uuids)
        val name = try { device.name } catch (_: SecurityException) { null }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable {
                if (mScanning) try { mBluetoothAdapter?.stopLeScan(mLeScanCallback) } catch (_: SecurityException) {}
                mScanning = false
                val ret = Intent().apply { putExtra("hrm_name", device.name); putExtra("hrm_address", device.address) }
                setResult(RESULT_OK, ret); finish()
            }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).padding(4.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (!name.isNullOrEmpty()) name else getString(R.string.ble_unknown_device), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
