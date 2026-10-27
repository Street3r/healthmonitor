package com.atakmap.android.healthmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceList {
    private static final String TAG = "DeviceList";

    private final Context mAtakContext;
    private List<String[]> devices;

    public DeviceList(Context atakContext) {
        mAtakContext = atakContext;
    }

    /**
     * Android 12+ needs the BLUETOOTH_CONNECT runtime permission (granted to ATAK,
     * since the plugin runs inside ATAK) before bonded devices can be listed or connected.
     */
    public static boolean hasBluetoothPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public List<String[]> getDevices() {
        devices = new ArrayList<>();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !hasBluetoothPermission(mAtakContext)) {
            Log.w(TAG, "Bluetooth not available or permission missing");
            return devices;
        }
        Set<BluetoothDevice> bluetoothDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bluetoothDevices) {
            if (device.getName() != null ) {
                devices.add(new String[] {device.getName(), device.getAddress()});
            }
        }

        return devices;
    }
}
