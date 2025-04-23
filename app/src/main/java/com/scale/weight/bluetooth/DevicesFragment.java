package com.scale.weight.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import java.util.ArrayList;
import java.util.Collections;

public class DevicesFragment extends ListFragment implements BluetoothConnectionTracker.ConnectionChangeListener {

    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<BluetoothDevice> listItems = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> listAdapter;
    private ActivityResultLauncher<String> requestBluetoothPermissionLauncherForRefresh;
    private Menu menu;
    private boolean permissionMissing;
    private ActivityResultLauncher<String> requestStoragePermissionLauncher;
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
                // Check both app-level and system-level connections
                boolean isConnected = TerminalFragment.isDeviceConnected(device) || connectionTracker.isSystemConnected(device);
                indicator.setBackgroundColor(isConnected
                        ? getResources().getColor(android.R.color.holo_green_dark)
                        : getResources().getColor(android.R.color.darker_gray));
                return view;
            }
        };

        requestBluetoothPermissionLauncherForRefresh = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> BluetoothUtil.onPermissionsResult(this, granted, this::refresh));

        requestStoragePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> BluetoothUtil.onStoragePermissionResult(this, granted, this::refresh));

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
        this.menu = menu;
        if (permissionMissing)
            menu.findItem(R.id.bt_refresh).setVisible(true);
        if (bluetoothAdapter == null)
            menu.findItem(R.id.bt_settings).setEnabled(false);
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
        } else if (id == R.id.bt_refresh) {
            if (BluetoothUtil.hasPermissions(this, requestBluetoothPermissionLauncherForRefresh))
                refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("MissingPermission")
    void refresh() {
        listItems.clear();
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            BluetoothUtil.hasStoragePermissions(this, requestStoragePermissionLauncher);
        }

        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionMissing = getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED;
                if (menu != null) menu.findItem(R.id.bt_refresh).setVisible(permissionMissing);
            }

            if (!permissionMissing) {
                for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                    // Include all paired devices, regardless of connection status
                    if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE) {
                        listItems.add(device);
                    }
                }
                Collections.sort(listItems, BluetoothUtil::compareTo);
            }
        }

        TextView emptyView = getView() != null ? getView().findViewById(android.R.id.empty) : null;
        if (emptyView != null) {
            if (bluetoothAdapter == null) {
                emptyView.setText("<bluetooth not supported>");
            } else if (!bluetoothAdapter.isEnabled()) {
                emptyView.setText("<bluetooth is disabled>");
            } else if (permissionMissing) {
                emptyView.setText("<permission missing, use REFRESH>");
            } else if (listItems.isEmpty()) {
                emptyView.setText("<no paired devices found>");
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
        refresh();
    }
}