package com.scale.weight.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.Fragment;

public class BluetoothUtil {

    interface PermissionGrantedCallback {
        void call();
    }

    /**
     * sort by name, then address. sort named devices first
     */
    @SuppressLint("MissingPermission")
    static int compareTo(BluetoothDevice a, BluetoothDevice b) {
        boolean aValid = a.getName()!=null && !a.getName().isEmpty();
        boolean bValid = b.getName()!=null && !b.getName().isEmpty();
        if(aValid && bValid) {
            int ret = a.getName().compareTo(b.getName());
            if (ret != 0) return ret;
            return a.getAddress().compareTo(b.getAddress());
        }
        if(aValid) return -1;
        if(bValid) return +1;
        return a.getAddress().compareTo(b.getAddress());
    }

    /**
     * Android 12 permission handling for Bluetooth
     */
    private static void showBluetoothRationaleDialog(Fragment fragment, DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title));
        builder.setMessage(fragment.getString(R.string.bluetooth_permission_grant));
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Continue", listener);
        builder.show();
    }

    /**
     * Storage permission handling
     */
    private static void showStorageRationaleDialog(Fragment fragment, DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle("Storage Permission Required");
        builder.setMessage("The app needs storage permission to save weight data to your device.");
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Continue", listener);
        builder.show();
    }

    private static void showSettingsDialog(Fragment fragment, String permissionType) {
        String message;
        if (permissionType.equals("bluetooth")) {
            String s = fragment.getResources().getString(fragment.getResources().getIdentifier("@android:string/permgrouplab_nearby_devices", null, null));
            message = String.format(fragment.getString(R.string.bluetooth_permission_denied), s);
        } else {
            message = "Storage permission is required to save weight data. Please enable it in Settings.";
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getActivity());
        builder.setTitle(permissionType.equals("bluetooth") ?
                fragment.getString(R.string.bluetooth_permission_title) : "Storage Permission Required");
        builder.setMessage(message);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Settings", (dialog, which) ->
                fragment.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + BuildConfig.APPLICATION_ID))));
        builder.show();
    }

    static boolean hasPermissions(Fragment fragment, ActivityResultLauncher<String> requestBluetoothPermissionLauncher) {
        // Check Bluetooth permissions for Android 12+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean missingBluetoothPermissions = fragment.getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
            boolean showBluetoothRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT);

            if(missingBluetoothPermissions) {
                if (showBluetoothRationale) {
                    showBluetoothRationaleDialog(fragment, (dialog, which) ->
                            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT));
                } else {
                    requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
                }
                return false;
            }
        }

        return true;
    }

    static boolean hasStoragePermissions(Fragment fragment, ActivityResultLauncher<String> requestStoragePermissionLauncher) {
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            boolean missingStoragePermissions = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                missingStoragePermissions = fragment.getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            }
            boolean showStorageRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            if(missingStoragePermissions) {
                if (showStorageRationale) {
                    showStorageRationaleDialog(fragment, (dialog, which) ->
                            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE));
                } else {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                return false;
            }
        }

        return true;
    }

    static void onPermissionsResult(Fragment fragment, boolean granted, PermissionGrantedCallback cb) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return;
        boolean showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT);
        if (granted) {
            cb.call();
        } else if (showRationale) {
            showBluetoothRationaleDialog(fragment, (dialog, which) -> cb.call());
        } else {
            showSettingsDialog(fragment, "bluetooth");
        }
    }

    static void onStoragePermissionResult(Fragment fragment, boolean granted, PermissionGrantedCallback cb) {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            return;
        boolean showRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (granted) {
            cb.call();
        } else if (showRationale) {
            showStorageRationaleDialog(fragment, (dialog, which) -> cb.call());
        } else {
            showSettingsDialog(fragment, "storage");
        }
    }
}