package com.scale.weight.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.ListFragment;

import java.util.ArrayList;
import java.util.Collections;

public class ConnectedDevicesFragment extends ListFragment implements BluetoothConnectionTracker.ConnectionChangeListener {

    private static final String TAG = "ConnectedDevicesFt";
    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<BluetoothDevice> listItems = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> listAdapter;
    private BluetoothConnectionTracker connectionTracker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        listAdapter = new ArrayAdapter<BluetoothDevice>(getActivity(), 0, listItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                BluetoothDevice device = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                View indicator = view.findViewById(R.id.connection_indicator);
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                text1.setText(deviceName != null ? deviceName : "Unknown Device");
                text2.setText(device.getAddress());
                // Device is definitely connected (since it's in the list), so set indicator to green
                indicator.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                return view;
            }
        };

        // Initialize BluetoothConnectionTracker
        connectionTracker = BluetoothConnectionTracker.getInstance(getActivity());
        connectionTracker.addConnectionChangeListener(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_devices_combined, container, false);
        ListView listView = view.findViewById(android.R.id.list);
        listView.setEmptyView(view.findViewById(android.R.id.empty));
        TextView emptyText = view.findViewById(android.R.id.empty);
        emptyText.setTextSize(18);
        emptyText.setText("initializing...");
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
        if (bluetoothAdapter == null)
            menu.findItem(R.id.bt_settings).setEnabled(false);
        // Hide the refresh option since we're only showing connected devices
        menu.findItem(R.id.bt_refresh).setVisible(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the connection tracker listener
        connectionTracker.removeConnectionChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.bt_settings) {
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("MissingPermission")
    public void refresh() {
        listItems.clear();

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                // Only include devices that are connected (either via app or system)
                if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE &&
                        (TerminalFragment.isDeviceConnected(device) || connectionTracker.isSystemConnected(device))) {
                    listItems.add(device);
                }
            }
            Collections.sort(listItems, BluetoothUtil::compareTo);
        }

        TextView emptyView = getView() != null ? getView().findViewById(android.R.id.empty) : null;
        if (emptyView != null) {
            if (bluetoothAdapter == null) {
                emptyView.setText("<bluetooth not supported>");
            } else if (!bluetoothAdapter.isEnabled()) {
                emptyView.setText("<bluetooth is disabled>");
            } else if (listItems.isEmpty()) {
                emptyView.setText("<no connected device>");
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        BluetoothDevice device = listItems.get(position);
        Intent intent = new Intent(getActivity(), TerminalActivity.class);
        intent.putExtra("device", device.getAddress());
        startActivity(intent);
    }

    @Override
    public void onConnectionStateChanged() {
        // Refresh the list when a system-level connection state changes
        Log.d(TAG, "Connection state changed, refreshing ConnectedDevicesFragment");
        refresh();
    }
}