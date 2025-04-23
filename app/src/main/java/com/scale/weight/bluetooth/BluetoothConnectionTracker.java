package com.scale.weight.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BluetoothConnectionTracker {
    private static final String TAG = "BTConnectionTracker";
    private static final Set<String> systemConnectedDevices = new HashSet<>();
    private static BluetoothConnectionTracker instance;
    private Context context;
    private final List<ConnectionChangeListener> listeners = new ArrayList<>();

    // Interface to notify listeners of connection state changes
    public interface ConnectionChangeListener {
        void onConnectionStateChanged();
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (device == null) return;

            String deviceAddress = device.getAddress();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.d(TAG, "System-level connection detected: " + deviceAddress);
                systemConnectedDevices.add(deviceAddress);
                notifyListeners();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.d(TAG, "System-level disconnection detected: " + deviceAddress);
                systemConnectedDevices.remove(deviceAddress);
                notifyListeners();
            }
        }
    };

    private BluetoothConnectionTracker(Context context) {
        this.context = context.getApplicationContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.context.registerReceiver(bluetoothReceiver, filter);
    }

    public static synchronized BluetoothConnectionTracker getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothConnectionTracker(context);
        }
        return instance;
    }

    public void addConnectionChangeListener(ConnectionChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeConnectionChangeListener(ConnectionChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ConnectionChangeListener listener : listeners) {
            listener.onConnectionStateChanged();
        }
    }

    public boolean isSystemConnected(BluetoothDevice device) {
        return device != null && systemConnectedDevices.contains(device.getAddress());
    }

    public void unregisterReceiver() {
        try {
            context.unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered: " + e.getMessage());
        }
    }
}
