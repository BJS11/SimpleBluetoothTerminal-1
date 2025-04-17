package com.scale.weight.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private static final String TAG = "TerminalFragment";

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private Connected connected = Connected.False;
    private boolean initialStart = true;

    private ActivityResultLauncher<String> requestStoragePermissionLauncher;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        requestStoragePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> BluetoothUtil.onStoragePermissionResult(this, granted, () -> {})
        );
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        Activity activity = getActivity();
        if (activity != null) {
            activity.stopService(new Intent(activity, SerialService.class));
        }
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else {
            Activity activity = getActivity();
            if (activity != null) {
                activity.startService(new Intent(activity, SerialService.class));
            }
        }
    }

    @Override
    public void onStop() {
        if (service != null && getActivity() != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        activity.bindService(new Intent(activity, SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            Activity activity = getActivity();
            if (activity != null) {
                activity.unbindService(this);
            }
        } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(this::connect);
            }
        }
    }

    /**
     * Service connection callbacks
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(this::connect);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);

        Context context = getContext();
        if (context != null) {
            receiveText.setTextColor(context.getResources().getColor(R.color.colorRecieveText));
        }
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            Activity activity = getActivity();
            if (activity != null) {
                SerialSocket socket = new SerialSocket(activity.getApplicationContext(), device);
                service.connect(socket);
            }
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        if (service != null) {
            service.disconnect();
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();

        byte[] lastData = datas.getLast();

        String msg = new String(lastData);

        String[] res = msg.split("\n");

        String processedValue = processValue(res[res.length-1]);
        receiveText.setText(processedValue);

        saveLastProcessedValue(processedValue);
    }

    private void status(String str) {
        Activity activity = getActivity();
        if (activity == null) return;

        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.colorStatusText)),
                0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.setText(spn);
    }

    private void showNotificationSettings() {
        Activity activity = getActivity();
        if (activity == null) return;

        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", activity.getPackageName());
        startActivity(intent);
    }

    private void showToast(String message) {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS}) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                service != null && !service.areNotificationsEnabled()) {
            showNotificationSettings();
        }
    }

    /*
     * SerialListener Implementation
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    public String processValue(String input) {
        if (input == null) return "";

        String result = input.trim();

        result = result.replaceFirst("^\\+", "");

        result = result.replaceAll("(?i)\\s*(oz|lb|g\\(t\\)|g|kg)$", "");

        return result.trim();
    }

    private void saveLastProcessedValue(String processedValue) {
        Activity activity = getActivity();
        if (activity == null) return;

        String fileName = "BT_WEIGHT_LOGS.txt";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = activity.getContentResolver();
                Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

                String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                        MediaStore.MediaColumns.RELATIVE_PATH + "=?";
                String relativePath = Environment.DIRECTORY_DOWNLOADS + "/";
                String[] selectionArgs = new String[]{fileName, relativePath};

                Cursor cursor = resolver.query(contentUri, null, selection, selectionArgs, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                        Uri deleteUri = ContentUris.withAppendedId(contentUri, id);
                        resolver.delete(deleteUri, null, null);
                    }
                    cursor.close();
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = resolver.insert(contentUri, values);
                if (uri != null) {
                    try (OutputStream os = resolver.openOutputStream(uri, "wt")) {
                        if (os != null) {
                            os.write(processedValue.getBytes());
                            Log.d(TAG, "File saved successfully via MediaStore");
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to create new file via MediaStore");
                    attemptDirectFileSave(activity, fileName, processedValue);
                }

            } else {
                if (BluetoothUtil.hasStoragePermissions(this, requestStoragePermissionLauncher)) {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs();
                    }

                    File file = new File(downloadsDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(file, false)) {
                        fos.write(processedValue.getBytes());
                        Log.d(TAG, "File saved via direct access");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving file: " + e.getMessage(), e);
        }
    }

    private void attemptDirectFileSave(Activity activity, String fileName, String content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "No storage permission, requesting...");
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    100);
            return;
        }

        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs()) {
                    Log.e(TAG, "Could not create Downloads directory");
                    return;
                }
            }

            File file = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(content.getBytes());
                Log.d(TAG, "File saved successfully via direct access");
            }
        } catch (IOException e) {
            Log.e(TAG, "Direct file save failed: " + e.getMessage(), e);
            showToast("Error saving file: " + e.getMessage());
        }
    }
}
