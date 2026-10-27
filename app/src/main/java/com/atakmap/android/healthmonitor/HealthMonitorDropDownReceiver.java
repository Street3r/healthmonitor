
package com.atakmap.android.healthmonitor;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.healthmonitor.plugin.HealthMonitorLifecycle;
import com.atakmap.android.healthmonitor.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class HealthMonitorDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "HealthMonitorDropDownReceiver";

    public static final String SHOW_PLUGIN = "com.atakmap.android.healthmonitor.SHOW_PLUGIN";
    private final View templateView;
    private final Context pluginContext;
    private Button scan;
    private TextView heartRateTV, name;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mBluetoothGattCharacteristic;
    public final BluetoothGattCallback mGattCallback;
    public static String HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public TableLayout healthTable;

    // Own position report with the heart rate is sent at most this often
    private static final long DISPATCH_INTERVAL_MS = 30_000;
    private long lastDispatch = 0;


    /**************************** CONSTRUCTOR *****************************/

    @SuppressLint("MissingPermission")
    public HealthMonitorDropDownReceiver(final MapView mapView,
                                          final Context context) {
        super(mapView);
        this.pluginContext = context;

        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        heartRateTV = templateView.findViewById(R.id.heartRate);
        healthTable = templateView.findViewById(R.id.table);
        name = templateView.findViewById(R.id.name);

        this.mGattCallback = new BluetoothGattCallback() {
            @Override  // android.bluetooth.BluetoothGattCallback
            public void onCharacteristicChanged(BluetoothGatt arg2, BluetoothGattCharacteristic characteristic) {
                final int heartRate = parseHeartRate(characteristic.getValue());
                if (heartRate < 0)
                    return;

                HealthMonitorLifecycle.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        heartRateTV.setText(String.format(Locale.US, "%d bpm", heartRate));
                    }
                });
                Marker self = getMapView().getSelfMarker();
                if (self == null)
                    return;
                self.setMetaInteger(HealthMonitorMapComponent.META_BPM, heartRate);
                self.setMetaLong(HealthMonitorMapComponent.META_BPM_TIME, SystemClock.elapsedRealtime());

                long now = SystemClock.elapsedRealtime();
                if (lastDispatch == 0 || now - lastDispatch >= DISPATCH_INTERVAL_MS) {
                    lastDispatch = now;
                    CotEvent saveMarker = CotEventFactory.createCotEvent(self);
                    CotMapComponent.getExternalDispatcher().dispatch(saveMarker);
                }
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onCharacteristicRead(BluetoothGatt arg1, BluetoothGattCharacteristic characteristic, int arg3) {
                Log.d(TAG, "onCharacteristicRead " + arg3);
                if(arg3 == 0) {
                }
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onCharacteristicWrite(BluetoothGatt arg2, BluetoothGattCharacteristic arg3, int arg4) {
            }

            @SuppressLint("ResourceAsColor")
            @Override  // android.bluetooth.BluetoothGattCallback
            public void onConnectionStateChange(BluetoothGatt arg5, int arg6, int arg7) {
                Log.d(TAG, "onConnectionStateChange status: " + arg6);
                Log.d(TAG, "onConnectionStateChange newState: " + arg7);
               if(arg7 == 0) {
                    Log.i(TAG, "Disconnected from GATT server.");
                    // Stop sending the last reading; the user reconnects from the button
                    Marker self = getMapView().getSelfMarker();
                    if (self != null)
                        self.removeMetaData(HealthMonitorMapComponent.META_BPM);
                    lastDispatch = 0;
                    closeGatt(arg5);
                    HealthMonitorLifecycle.getActivity().runOnUiThread(() -> {
                        scan.setBackgroundResource(R.color.red);
                        scan.setText("*NOT* Connected");
                        scan.setVisibility(View.VISIBLE);
                    });

                }

               if(arg7 == 2) {
                   HealthMonitorLifecycle.getActivity().runOnUiThread(() -> {
                       scan.setBackgroundResource(R.color.green);
                       scan.setText("Connected");
                       scan.setVisibility(View.VISIBLE);
                   });

                   arg5.discoverServices();
               }

                super.onConnectionStateChange(arg5, arg6, arg7);
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onDescriptorRead(BluetoothGatt arg1, BluetoothGattDescriptor arg2, int arg3) {
                super.onDescriptorRead(arg1, arg2, arg3);
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onDescriptorWrite(BluetoothGatt arg3, BluetoothGattDescriptor arg4, int arg5) {
                Log.d(TAG, "onDescriptorWrite status: " + arg5);
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onMtuChanged(BluetoothGatt arg2, int arg3, int arg4) {
                super.onMtuChanged(arg2, arg3, arg4);
                if(arg4 == 0) {
                    boolean v2 = arg2.discoverServices();
                    Log.i(TAG, "5.0 attempting to start service discovery: " + v2);
                    return;
                }

                Log.i(TAG, "Failed to set MTU.");
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onReadRemoteRssi(BluetoothGatt arg2, int arg3, int arg4) {
                Log.i(TAG, "rssi = " + arg3);
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onReliableWriteCompleted(BluetoothGatt arg1, int arg2) {
                super.onReliableWriteCompleted(arg1, arg2);
            }

            @Override  // android.bluetooth.BluetoothGattCallback
            public void onServicesDiscovered(BluetoothGatt arg3, int arg4) {
                Log.w(TAG, "onServicesDiscovered received: " + arg4);

                BluetoothGattService s = arg3.getService(UUID.fromString(HEART_RATE));
                if (s == null) {
                    arg3.disconnect();
                    return;
                }
                mBluetoothGattCharacteristic = s.getCharacteristic(UUID.fromString(HEART_RATE_MEASUREMENT));
                if (mBluetoothGattCharacteristic == null) {
                    arg3.disconnect();
                    return;
                }
                arg3.setCharacteristicNotification(mBluetoothGattCharacteristic, true);
                BluetoothGattDescriptor d = mBluetoothGattCharacteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                if (d == null) {
                    Log.w(TAG, "Heart rate characteristic has no CCC descriptor");
                    return;
                }
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                arg3.writeDescriptor(d);
            }
        };

        scan = templateView.findViewById(R.id.Scan);

        scan.setOnClickListener(v -> {
            SharedPreferences sharedPreference = PreferenceManager.getDefaultSharedPreferences(mapView.getContext());
            String key = sharedPreference.getString("plugin_health_monitor_key_set_comm_device", "");
            if (key.isEmpty()) {
                Toast toast = Toast.makeText(context, "No BLE device set", Toast.LENGTH_LONG);
                toast.show();
                return;
            }
            if(!scan.getText().toString().contains("*NOT*")) {
                Toast toast = Toast.makeText(context, "Already connected", Toast.LENGTH_LONG);
                toast.show();
                return;
            }
            /*
            if(!scan.getText().toString().contains("*NOT*")) {
                mBluetoothGatt.disconnect();
                scan.setBackgroundResource(R.color.red);
                scan.setText("Scan BLE *NOT* Connected");
                scan.setVisibility(View.VISIBLE);
                name.setText("Connected to:");
                return;
            }
            */

            if (!DeviceList.hasBluetoothPermission(mapView.getContext())) {
                Toast.makeText(context, "ATAK has no Bluetooth permission (Nearby devices)", Toast.LENGTH_LONG).show();
                return;
            }
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Toast.makeText(context, "Bluetooth is off", Toast.LENGTH_LONG).show();
                return;
            }

            Set<BluetoothDevice> bluetoothDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bluetoothDevices) {
                if (device.getName() != null) {
                   if (device.getAddress().equals(key)) {
                       // A previous connection attempt may still hold a GATT client
                       closeGatt();
                       mBluetoothGatt = device.connectGatt(mapView.getContext(), true, mGattCallback);
                       HealthMonitorLifecycle.getActivity().runOnUiThread(() -> name.setText("Connected to: " + device.getName()));
                   }
                }
            }
        });
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
        closeGatt();
    }

    /**
     * Parses a BLE Heart Rate Measurement (0x2A37). Bit 0 of the flags byte tells
     * whether the value is UINT8 or UINT16 (little endian).
     * @return heart rate in bpm, or -1 if the value is malformed
     */
    static int parseHeartRate(byte[] value) {
        if (value == null || value.length < 2)
            return -1;
        if ((value[0] & 0x01) != 0) {
            if (value.length < 3)
                return -1;
            return (value[1] & 0xFF) | ((value[2] & 0xFF) << 8);
        }
        return value[1] & 0xFF;
    }

    @SuppressLint("MissingPermission")
    private synchronized void closeGatt() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    @SuppressLint("MissingPermission")
    private synchronized void closeGatt(BluetoothGatt gatt) {
        if (gatt == mBluetoothGatt)
            mBluetoothGatt = null;
        gatt.close();
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down");
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

}
