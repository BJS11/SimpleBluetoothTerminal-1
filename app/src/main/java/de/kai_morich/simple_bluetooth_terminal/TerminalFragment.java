package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private static final String DATE_FORMAT = "MMM dd, yyyy - HH:mm:ss.sss";
    private static final String FILENAME_FORMAT = "yyyyMMdd_HHmmss";

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private final SimpleDateFormat timestampFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
    private final SimpleDateFormat fileNameFormat = new SimpleDateFormat(FILENAME_FORMAT, Locale.US);

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted) {
                    showToast("Storage permission denied, cannot save file");
                }
            });

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
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

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem notificationItem = menu.findItem(R.id.backgroundNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationItem.setChecked(service != null && service.areNotificationsEnabled());
        } else {
            notificationItem.setChecked(true);
            notificationItem.setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.backgroundNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                } else {
                    showNotificationSettings();
                }
            }
            return true;
        } else if (id == R.id.save_last_value) {
            saveFile(true);
            return true;
        } else if (id == R.id.save_as_full) {
            saveFile(false);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
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

        for (byte[] data : datas) {
            String timestamp = timestampFormat.format(new Date());
            String msg = new String(data);

            // Process newlines
            if (newline.equals(TextUtil.newline_crlf) && !msg.isEmpty()) {
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                if (pendingNewline && msg.charAt(0) == '\n') {
                    // Handle pending newline
                    if (spn.length() >= 2) {
                        spn.delete(spn.length() - 2, spn.length());
                    } else {
                        Editable edt = receiveText.getEditableText();
                        if (edt != null && edt.length() >= 2) {
                            edt.delete(edt.length() - 2, edt.length());
                        }
                    }
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }

            String processedMsg = (String) TextUtil.toCaretString(msg, newline.length() != 0);
            String displayMsg = timestamp + " " + processedMsg;
            Log.d(TAG, "Appending to receiveText: " + displayMsg);
            spn.append(displayMsg);
        }

        receiveText.append(spn);
    }

    private void status(String str) {
        Activity activity = getActivity();
        if (activity == null) return;

        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.colorStatusText)),
                0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void showNotificationSettings() {
        Activity activity = getActivity();
        if (activity == null) return;

        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", activity.getPackageName());
        startActivity(intent);
    }

    private void saveFile(boolean isLastValue) {
        Activity activity = getActivity();
        if (activity == null) return;

        String timestamp = fileNameFormat.format(new Date());
        String fileName = "BTLOGS_" + timestamp + ".txt";
        String content;

        if (isLastValue) {
            String fullText = receiveText.getText().toString();
            if (fullText.isEmpty()) {
                showToast("No data to save");
                return;
            }

            String[] lines = fullText.split("\n");
            String lastLine = "";
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!lines[i].trim().isEmpty()) {
                    lastLine = lines[i].trim();
                    break;
                }
            }

            if (lastLine.isEmpty()) {
                showToast("No data to save");
                return;
            }
            content = lastLine;
            Log.d(TAG, "Saving last value: " + content);
        } else {
            content = receiveText.getText().toString();
            if (content.isEmpty()) {
                showToast("No data to save");
                return;
            }
            Log.d(TAG, "Saving full content length: " + content.length());
        }

        String formattedDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date());
        String formattedTime = new SimpleDateFormat("HH:mm:ss.sss", Locale.US).format(new Date());
        String formattedContent = formattedDate + " " + formattedTime + "\n" + content;

        saveFileToStorage(activity, fileName, formattedContent);
    }

    private void saveFileToStorage(Activity activity, String fileName, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveFileWithMediaStore(activity, fileName, content);
            } else {
                saveFileWithDirectAccess(activity, fileName, content);
            }
        } catch (IOException e) {
            showToast("Error saving file: " + e.getMessage());
        }
    }

    private void saveFileWithMediaStore(Activity activity, String fileName, String content) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        if (uri != null) {
            try (OutputStream outputStream = activity.getContentResolver().openOutputStream(uri)) {
                if (outputStream != null) {
                    outputStream.write(content.getBytes());
                }
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            activity.getContentResolver().update(uri, values, null, null);
            showToast("File saved to Downloads");
        } else {
            showToast("Failed to create file");
        }
    }

    private void saveFileWithDirectAccess(Activity activity, String fileName, String content) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes());
                showToast("File saved to Downloads");
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            showToast("Storage permission required");
        }
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
}
