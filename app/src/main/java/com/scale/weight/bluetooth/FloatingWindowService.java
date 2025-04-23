//package com.scale.weight.bluetooth;
//
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.PendingIntent;
//import android.app.Service;
//import android.bluetooth.BluetoothDevice;
//import android.content.ComponentName;
//import android.content.ContentResolver;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.Intent;
//import android.content.ServiceConnection;
//import android.database.Cursor;
//import android.graphics.PixelFormat;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.provider.MediaStore;
//import android.util.Log;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.WindowManager;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import androidx.core.app.NotificationCompat;
//
//import java.io.File;
//import java.io.OutputStream;
//import java.util.ArrayDeque;
//
//public class FloatingWindowService extends Service implements SerialListener {
//
//    private static final String CHANNEL_ID = "FloatingWindowServiceChannel";
//    private static final int NOTIFICATION_ID = 1;
//    private static final String TAG = "FloatingWindowService";
//
//    private WindowManager windowManager;
//    private View floatingView;
//    private TextView streamingText;
//    private LinearLayout notConnectedLayout;
//    private SerialService serialService;
//    private boolean isServiceBound = false;
//    private String currentDeviceAddress;
//    private Handler mainHandler;
//    private Handler connectionCheckHandler;
//
//    private final Runnable connectionChecker = new Runnable() {
//        @Override
//        public void run() {
//            updateFloatingWindowState();
//            connectionCheckHandler.postDelayed(this, 5000); // Check every 5 seconds
//        }
//    };
//
//    private final ServiceConnection serviceConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            SerialService.SerialBinder binder = (SerialService.SerialBinder) service;
//            serialService = binder.getService();
//            serialService.attach(FloatingWindowService.this);
//            isServiceBound = true;
//            Log.d(TAG, "Service bound, attached to SerialService");
//            updateFloatingWindowState();
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            isServiceBound = false;
//            serialService = null;
//            Log.d(TAG, "Service unbound from SerialService");
//        }
//    };
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        createNotificationChannel();
//        startForeground(NOTIFICATION_ID, createNotification());
//
//        // Initialize Handlers for UI updates and connection checks
//        mainHandler = new Handler(Looper.getMainLooper());
//        connectionCheckHandler = new Handler(Looper.getMainLooper());
//
//        // Initialize WindowManager and floating view
//        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
//        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);
//
//        // Setup floating window parameters
//        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
//                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
//                        WindowManager.LayoutParams.TYPE_PHONE,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                PixelFormat.TRANSLUCENT);
//
//        params.gravity = Gravity.TOP | Gravity.START;
//        params.x = 0; // Top-left corner
//        params.y = 0;
//
//        // Initialize views
//        streamingText = floatingView.findViewById(R.id.receive_text);
//        notConnectedLayout = floatingView.findViewById(R.id.not_connected_layout);
//
//        // Make the floating window clickable
//        floatingView.setOnClickListener(v -> {
//            // Navigate to MainActivity with DevicesFragment
//            Intent intent = new Intent(FloatingWindowService.this, MainActivity.class);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.putExtra("showDevicesFragment", true);
//            startActivity(intent);
//
//            // Stop the service and remove the floating window
//            stopSelf();
//        });
//
//        // Add the floating view to the window manager
//        windowManager.addView(floatingView, params);
//
//        // Bind to SerialService
//        Intent intent = new Intent(this, SerialService.class);
//        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
//
//        // Start periodic connection checks
//        connectionCheckHandler.post(connectionChecker);
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent != null && intent.hasExtra("deviceAddress")) {
//            currentDeviceAddress = intent.getStringExtra("deviceAddress");
//            Log.d(TAG, "Starting FloatingWindowService with deviceAddress: " + currentDeviceAddress);
//            updateFloatingWindowState();
//        } else {
//            Log.e(TAG, "Intent or deviceAddress is null");
//        }
//        return START_STICKY;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//
//        // Remove connection check callbacks
//        connectionCheckHandler.removeCallbacks(connectionChecker);
//
//        if (floatingView != null && windowManager != null) {
//            try {
//                windowManager.removeView(floatingView);
//            } catch (IllegalArgumentException e) {
//                Log.e(TAG, "View already removed", e);
//            }
//            floatingView = null;
//        }
//
//        if (isServiceBound) {
//            try {
//                serialService.detach();
//                unbindService(serviceConnection);
//            } catch (Exception e) {
//                Log.e(TAG, "Error unbinding service", e);
//            }
//            isServiceBound = false;
//        }
//
//        Log.d(TAG, "FloatingWindowService destroyed");
//    }
//
//    @Override
//    public void onTaskRemoved(Intent rootIntent) {
//        super.onTaskRemoved(rootIntent);
//        stopSelf();
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    private void createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    CHANNEL_ID,
//                    "Floating Window Service",
//                    NotificationManager.IMPORTANCE_LOW
//            );
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            manager.createNotificationChannel(channel);
//        }
//    }
//
//    private Notification createNotification() {
//        Intent intent = new Intent(this, MainActivity.class);
//        intent.putExtra("showDevicesFragment", true);
//        PendingIntent pendingIntent = PendingIntent.getActivity(
//                this,
//                0,
//                intent,
//                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
//        );
//
//        return new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("Bluetooth Weight App")
//                .setContentText("Running in background")
//                .setSmallIcon(android.R.drawable.ic_dialog_info)
//                .setContentIntent(pendingIntent)
//                .setOngoing(true)
//                .build();
//    }
//
//    private void updateFloatingWindowState() {
//        if (serialService == null) {
//            Log.d(TAG, "SerialService is null, cannot update floating window state");
//            return;
//        }
//
//        mainHandler.post(() -> {
//            try {
//                BluetoothDevice currentDevice = serialService.getCurrentDevice();
//                if (currentDevice != null && currentDeviceAddress != null &&
//                        currentDevice.getAddress().equals(currentDeviceAddress)) {
//                    // Device is connected, show streaming data
//                    notConnectedLayout.setVisibility(View.GONE);
//                    streamingText.setVisibility(View.VISIBLE);
//                    Log.d(TAG, "Device connected, showing streaming data");
//                } else {
//                    // No device connected, show "Not Connected" state
//                    streamingText.setVisibility(View.GONE);
//                    notConnectedLayout.setVisibility(View.VISIBLE);
//                    Log.d(TAG, "No device connected, showing 'Not Connected' state");
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error updating floating window state", e);
//            }
//        });
//    }
//
//    private void updateStreamingText(String data) {
//        mainHandler.post(() -> {
//            try {
//                if (streamingText != null) {
//                    streamingText.setVisibility(View.VISIBLE);
//                    streamingText.setText(data);
//                    Log.d(TAG, "Updated streaming text: " + data);
//                } else {
//                    Log.w(TAG, "streamingText is null, cannot update");
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error updating streaming text", e);
//            }
//        });
//    }
//
//    // SerialListener Implementation
//    @Override
//    public void onSerialConnect() {
//        Log.d(TAG, "onSerialConnect called");
//        mainHandler.post(this::updateFloatingWindowState);
//    }
//
//    @Override
//    public void onSerialConnectError(Exception e) {
//        Log.e(TAG, "onSerialConnectError: " + e.getMessage());
//        mainHandler.post(this::updateFloatingWindowState);
//    }
//
//    @Override
//    public void onSerialRead(byte[] data) {
//        Log.d(TAG, "onSerialRead(byte[]) called");
//        try {
//            if (serialService != null && serialService.getCurrentDevice() != null &&
//                    currentDeviceAddress != null &&
//                    serialService.getCurrentDevice().getAddress().equals(currentDeviceAddress)) {
//                ArrayDeque<byte[]> datas = new ArrayDeque<>();
//                datas.add(data);
//                processReceivedData(datas);
//            } else {
//                Log.w(TAG, "onSerialRead(byte[]): Device mismatch or serialService is null");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error in onSerialRead(byte[])", e);
//        }
//    }
//
//    @Override
//    public void onSerialRead(ArrayDeque<byte[]> datas) {
//        Log.d(TAG, "onSerialRead(ArrayDeque) called");
//        try {
//            if (serialService != null && serialService.getCurrentDevice() != null &&
//                    currentDeviceAddress != null &&
//                    serialService.getCurrentDevice().getAddress().equals(currentDeviceAddress)) {
//                processReceivedData(datas);
//            } else {
//                Log.w(TAG, "onSerialRead(ArrayDeque): Device mismatch or serialService is null");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error in onSerialRead(ArrayDeque)", e);
//        }
//    }
//
//    @Override
//    public void onSerialIoError(Exception e) {
//        Log.e(TAG, "onSerialIoError: " + e.getMessage());
//        mainHandler.post(this::updateFloatingWindowState);
//    }
//
//    private void processReceivedData(ArrayDeque<byte[]> datas) {
//        Log.d(TAG, "Received data in FloatingWindowService, processing...");
//
//        try {
//            // Process all incoming data instead of just the last chunk
//            StringBuilder completeMessage = new StringBuilder();
//            for (byte[] data : datas) {
//                completeMessage.append(new String(data));
//            }
//
//            String msg = completeMessage.toString();
//            Log.d(TAG, "Raw data: " + msg);
//
//            if (msg.contains("\n")) {
//                String[] lines = msg.split("\n");
//                String latestLine = lines[lines.length - 1].trim();
//
//                // Get the last non-empty line
//                for (int i = lines.length - 1; i >= 0; i--) {
//                    if (!lines[i].trim().isEmpty()) {
//                        latestLine = lines[i].trim();
//                        break;
//                    }
//                }
//
//                if (!latestLine.isEmpty()) {
//                    String processedValue = processValue(latestLine);
//                    Log.d(TAG, "Processed value: " + processedValue);
//
//                    // Ensure UI updates happen on main thread
//                    updateStreamingText(processedValue);
//                    saveProcessedValue(processedValue);
//                }
//            } else if (!msg.trim().isEmpty()) {
//                // Handle case where there's no newline
//                String processedValue = processValue(msg.trim());
//                Log.d(TAG, "Processed single line value: " + processedValue);
//                updateStreamingText(processedValue);
//                saveProcessedValue(processedValue);
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error processing received data", e);
//        }
//    }
//
//    private String processValue(String input) {
//        if (input == null) return "";
//
//        String result = input.trim();
//        result = result.replaceFirst("^\\+", "");
//        result = result.replaceAll("(?i)\\s*(oz|lb|g\\(t\\)|g|kg)$", "");
//        return result.trim();
//    }
//
//    private void saveProcessedValue(String processedValue) {
//        String fileName = "BT_WEIGHT_LOGS.txt";
//
//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                ContentResolver resolver = getContentResolver();
//                Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
//
//                String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
//                        MediaStore.MediaColumns.RELATIVE_PATH + "=?";
//                String relativePath = Environment.DIRECTORY_DOWNLOADS + "/";
//                String[] selectionArgs = new String[]{fileName, relativePath};
//
//                Cursor cursor = resolver.query(contentUri, null, selection, selectionArgs, null);
//                if (cursor != null) {
//                    while (cursor.moveToNext()) {
//                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
//                        Uri deleteUri = ContentUris.withAppendedId(contentUri, id);
//                        resolver.delete(deleteUri, null, null);
//                    }
//                    cursor.close();
//                }
//
//                ContentValues values = new ContentValues();
//                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
//                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
//                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
//
//                Uri uri = resolver.insert(contentUri, values);
//                if (uri != null) {
//                    try (OutputStream os = resolver.openOutputStream(uri, "wt")) {
//                        if (os != null) {
//                            os.write(processedValue.getBytes());
//                            Log.d(TAG, "File saved successfully via MediaStore");
//                        }
//                    }
//                } else {
//                    Log.e(TAG, "Failed to create new file via MediaStore");
//                }
//            } else {
//                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//                if (!downloadsDir.exists()) {
//                    downloadsDir.mkdirs();
//                }
//
//                File file = new File(downloadsDir, fileName);
//                try (OutputStream os = new java.io.FileOutputStream(file, false)) {
//                    os.write(processedValue.getBytes());
//                    Log.d(TAG, "File saved via direct access");
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error saving file: " + e.getMessage(), e);
//        }
//    }
//}
//
////package com.scale.weight.bluetooth;
////
////import android.app.Notification;
////import android.app.NotificationChannel;
////import android.app.NotificationManager;
////import android.app.PendingIntent;
////import android.app.Service;
////import android.bluetooth.BluetoothDevice;
////import android.content.ComponentName;
////import android.content.ContentResolver;
////import android.content.ContentUris;
////import android.content.ContentValues;
////import android.content.Context;
////import android.content.Intent;
////import android.content.ServiceConnection;
////import android.database.Cursor;
////import android.graphics.PixelFormat;
////import android.net.Uri;
////import android.os.Build;
////import android.os.Environment;
////import android.os.Handler;
////import android.os.IBinder;
////import android.os.Looper;
////import android.provider.MediaStore;
////import android.util.Log;
////import android.view.Gravity;
////import android.view.LayoutInflater;
////import android.view.View;
////import android.view.WindowManager;
////import android.widget.LinearLayout;
////import android.widget.TextView;
////
////import androidx.core.app.NotificationCompat;
////
////import java.io.File;
////import java.io.OutputStream;
////import java.util.ArrayDeque;
////
////public class FloatingWindowService extends Service implements SerialListener {
////
////    private static final String CHANNEL_ID = "FloatingWindowServiceChannel";
////    private static final int NOTIFICATION_ID = 1;
////    private static final String TAG = "FloatingWindowService";
////
////    private WindowManager windowManager;
////    private View floatingView;
////    private TextView streamingText;
////    private LinearLayout notConnectedLayout;
////    private SerialService serialService;
////    private boolean isServiceBound = false;
////    private String currentDeviceAddress;
////    private Handler mainHandler;
////
////    private final ServiceConnection serviceConnection = new ServiceConnection() {
////        @Override
////        public void onServiceConnected(ComponentName name, IBinder service) {
////            SerialService.SerialBinder binder = (SerialService.SerialBinder) service;
////            serialService = binder.getService();
////            serialService.attach(FloatingWindowService.this);
////            isServiceBound = true;
////            updateFloatingWindowState();
////        }
////
////        @Override
////        public void onServiceDisconnected(ComponentName name) {
////            isServiceBound = false;
////            serialService = null;
////        }
////    };
////
////    @Override
////    public void onCreate() {
////        super.onCreate();
////        createNotificationChannel();
////        startForeground(NOTIFICATION_ID, createNotification());
////
////        // Initialize Handler for UI updates
////        mainHandler = new Handler(Looper.getMainLooper());
////
////        // Initialize WindowManager and floating view
////        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
////        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);
////
////        // Setup floating window parameters
////        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
////                WindowManager.LayoutParams.WRAP_CONTENT,
////                WindowManager.LayoutParams.WRAP_CONTENT,
////                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
////                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
////                        WindowManager.LayoutParams.TYPE_PHONE,
////                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
////                PixelFormat.TRANSLUCENT);
////
////        params.gravity = Gravity.TOP | Gravity.START;
////        params.x = 0; // Top-left corner
////        params.y = 0;
////
////        // Initialize views
////        streamingText = floatingView.findViewById(R.id.receive_text);
////        notConnectedLayout = floatingView.findViewById(R.id.not_connected_layout);
////
////        // Make the floating window clickable
////        floatingView.setOnClickListener(v -> {
////            // Navigate to MainActivity with DevicesFragment
////            Intent intent = new Intent(FloatingWindowService.this, MainActivity.class);
////            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////            intent.putExtra("showDevicesFragment", true);
////            startActivity(intent);
////
////            // Stop the service and remove the floating window
////            stopSelf();
////        });
////
////        // Add the floating view to the window manager
////        windowManager.addView(floatingView, params);
////
////        // Bind to SerialService
////        Intent intent = new Intent(this, SerialService.class);
////        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
////    }
////
////    @Override
////    public int onStartCommand(Intent intent, int flags, int startId) {
////        currentDeviceAddress = intent.getStringExtra("deviceAddress");
////        updateFloatingWindowState();
////        return START_STICKY;
////    }
////
////    @Override
////    public void onDestroy() {
////        super.onDestroy();
////        if (floatingView != null) {
////            windowManager.removeView(floatingView);
////            floatingView = null;
////        }
////        if (isServiceBound) {
////            unbindService(serviceConnection);
////            isServiceBound = false;
////        }
////    }
////
////    @Override
////    public void onTaskRemoved(Intent rootIntent) {
////        super.onTaskRemoved(rootIntent);
////        // Stop the service when the app is killed
////        stopSelf();
////    }
////
////    @Override
////    public IBinder onBind(Intent intent) {
////        return null;
////    }
////
////    private void createNotificationChannel() {
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
////            NotificationChannel channel = new NotificationChannel(
////                    CHANNEL_ID,
////                    "Floating Window Service",
////                    NotificationManager.IMPORTANCE_LOW
////            );
////            NotificationManager manager = getSystemService(NotificationManager.class);
////            manager.createNotificationChannel(channel);
////        }
////    }
////
////    private Notification createNotification() {
////        Intent intent = new Intent(this, MainActivity.class);
////        intent.putExtra("showDevicesFragment", true);
////        PendingIntent pendingIntent = PendingIntent.getActivity(
////                this,
////                0,
////                intent,
////                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
////        );
////
////        return new NotificationCompat.Builder(this, CHANNEL_ID)
////                .setContentTitle("Bluetooth Weight App")
////                .setContentText("Running in background")
////                .setSmallIcon(android.R.drawable.ic_dialog_info)
////                .setContentIntent(pendingIntent)
////                .setOngoing(true)
////                .build();
////    }
////
////    private void updateFloatingWindowState() {
////        if (serialService == null) return;
////
////        BluetoothDevice currentDevice = serialService.getCurrentDevice();
////        if (currentDevice != null && currentDevice.getAddress().equals(currentDeviceAddress)) {
////            // Device is connected, show streaming data
////            notConnectedLayout.setVisibility(View.GONE);
////            streamingText.setVisibility(View.VISIBLE);
////        } else {
////            // No device connected, show "Not Connected" state
////            streamingText.setVisibility(View.GONE);
////            notConnectedLayout.setVisibility(View.VISIBLE);
////        }
////    }
////
////    private void updateStreamingText(String data) {
////        mainHandler.post(() -> {
////            if (streamingText != null) {
////                streamingText.setText(data);
////                Log.d(TAG, "Updated streaming text: " + data);
////            }
////        });
////    }
////
////    // SerialListener Implementation
////    @Override
////    public void onSerialConnect() {
////        mainHandler.post(this::updateFloatingWindowState);
////    }
////
////    @Override
////    public void onSerialConnectError(Exception e) {
////        mainHandler.post(this::updateFloatingWindowState);
////    }
////
////    @Override
////    public void onSerialRead(byte[] data) {
////        if (serialService != null && serialService.getCurrentDevice() != null &&
////                serialService.getCurrentDevice().getAddress().equals(currentDeviceAddress)) {
////            ArrayDeque<byte[]> datas = new ArrayDeque<>();
////            datas.add(data);
////            processReceivedData(datas);
////        }
////    }
////
////    @Override
////    public void onSerialRead(ArrayDeque<byte[]> datas) {
////        if (serialService != null && serialService.getCurrentDevice() != null &&
////                serialService.getCurrentDevice().getAddress().equals(currentDeviceAddress)) {
////            processReceivedData(datas);
////        }
////    }
////
////    @Override
////    public void onSerialIoError(Exception e) {
////        mainHandler.post(this::updateFloatingWindowState);
////    }
////
////    private void processReceivedData(ArrayDeque<byte[]> datas) {
////        Log.d(TAG, "Received data in FloatingWindowService");
////        byte[] lastData = datas.getLast();
////        String msg = new String(lastData);
////        String[] res = msg.split("\n");
////        String processedValue = processValue(res[res.length - 1]);
////        updateStreamingText(processedValue);
////        saveProcessedValue(processedValue);
////    }
////
////    private String processValue(String input) {
////        if (input == null) return "";
////
////        String result = input.trim();
////        result = result.replaceFirst("^\\+", "");
////        result = result.replaceAll("(?i)\\s*(oz|lb|g\\(t\\)|g|kg)$", "");
////        return result.trim();
////    }
////
////    private void saveProcessedValue(String processedValue) {
////        String fileName = "BT_WEIGHT_LOGS.txt";
////
////        try {
////            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
////                ContentResolver resolver = getContentResolver();
////                Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
////
////                String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
////                        MediaStore.MediaColumns.RELATIVE_PATH + "=?";
////                String relativePath = Environment.DIRECTORY_DOWNLOADS + "/";
////                String[] selectionArgs = new String[]{fileName, relativePath};
////
////                Cursor cursor = resolver.query(contentUri, null, selection, selectionArgs, null);
////                if (cursor != null) {
////                    while (cursor.moveToNext()) {
////                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
////                        Uri deleteUri = ContentUris.withAppendedId(contentUri, id);
////                        resolver.delete(deleteUri, null, null);
////                    }
////                    cursor.close();
////                }
////
////                ContentValues values = new ContentValues();
////                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
////                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
////                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
////
////                Uri uri = resolver.insert(contentUri, values);
////                if (uri != null) {
////                    try (OutputStream os = resolver.openOutputStream(uri, "wt")) {
////                        if (os != null) {
////                            os.write(processedValue.getBytes());
////                            Log.d(TAG, "File saved successfully via MediaStore");
////                        }
////                    }
////                } else {
////                    Log.e(TAG, "Failed to create new file via MediaStore");
////                }
////            } else {
////                // For older versions, direct file access (assuming permission is granted)
////                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
////                if (!downloadsDir.exists()) {
////                    downloadsDir.mkdirs();
////                }
////
////                File file = new File(downloadsDir, fileName);
////                try (OutputStream os = new java.io.FileOutputStream(file, false)) {
////                    os.write(processedValue.getBytes());
////                    Log.d(TAG, "File saved via direct access");
////                }
////            }
////        } catch (Exception e) {
////            Log.e(TAG, "Error saving file: " + e.getMessage(), e);
////        }
////    }
////}