package com.scale.weight.bluetooth;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "TrialPrefs";
    private static final String TRIAL_CHECKED = "TrialChecked";

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_STORAGE = 101;
    private static final int PERMISSION_REQUEST_BLUETOOTH = 102;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private TabLayout tabLayout;
    private BluetoothConnectionTracker connectionTracker;
    private DevicesFragment devicesFragment;
    private ConnectedDevicesFragment connectedDevicesFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (isTrialExpired()) {
            showTrialExpiredDialog();
        }

        // Initialize UI components
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navView = findViewById(R.id.nav_view);
        tabLayout = findViewById(R.id.tabLayout);

        // Initialize BluetoothConnectionTracker
        connectionTracker = BluetoothConnectionTracker.getInstance(this);

        // Initialize fragments
        devicesFragment = new DevicesFragment();
        connectedDevicesFragment = new ConnectedDevicesFragment();

        // Setup Navigation Drawer
        setupNavigationDrawer(toolbar);

        // Setup Tab Layout
        setupTabLayout();

        // Handle Navigation Drawer item clicks
        navView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_devices) {
                // Reset to devices tab when navigation item is selected
                tabLayout.selectTab(tabLayout.getTabAt(0));
            } else if (itemId == R.id.nav_info) {
                startActivity(new Intent(MainActivity.this, InfoActivity.class));
            } else if (itemId == R.id.nav_file) {
                startActivity(new Intent(MainActivity.this, FileActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Request necessary permissions
        checkAndRequestPermissions();
    }

    private boolean isTrialExpired() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean trialChecked = prefs.getBoolean(TRIAL_CHECKED, false);

        if (!trialChecked) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(TRIAL_CHECKED, true);
            editor.apply();
        }

        Calendar trialStart = Calendar.getInstance();
        trialStart.set(2025, Calendar.APRIL, 24, 0, 0, 0);
        trialStart.set(Calendar.MILLISECOND, 0);

        Calendar trialEnd = Calendar.getInstance();
        trialEnd.set(2025, Calendar.APRIL, 30, 23, 59, 59);
        trialEnd.set(Calendar.MILLISECOND, 999);

        Calendar currentTime = Calendar.getInstance();

        return currentTime.before(trialStart) || currentTime.after(trialEnd);
    }

    private void showTrialExpiredDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Trial Expired")
                .setMessage("The trial period has been ended. Please contact the app developer to continue using it.")
                .setPositiveButton("OK", (d, which) -> {
                    finish();
                })
                .setCancelable(false)
                .create();

        dialog.show();

        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(getResources().getColor(android.R.color.white));
        }
    }

    private void setupNavigationDrawer(Toolbar toolbar) {
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        ) {
            @Override
            public void onDrawerOpened(android.view.View drawerView) {
                super.onDrawerOpened(drawerView);
                toggle.setHomeAsUpIndicator(R.drawable.ic_menu);
                toggle.syncState();
            }

            @Override
            public void onDrawerClosed(android.view.View drawerView) {
                super.onDrawerClosed(drawerView);
                toggle.setHomeAsUpIndicator(R.drawable.ic_menu);
                toggle.syncState();
            }
        };
        drawerLayout.addDrawerListener(toggle);
        toggle.setDrawerIndicatorEnabled(true);
        toggle.setToolbarNavigationClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        toggle.syncState();
    }

    private void setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("Paired Devices"));
        tabLayout.addTab(tabLayout.newTab().setText("Connected Device"));

        // Set initial fragment
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            showFragment(devicesFragment);
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        showFragment(devicesFragment);
                        Log.d(TAG, "Switched to Paired Devices tab");
                        break;
                    case 1:
                        showFragment(connectedDevicesFragment);
                        Log.d(TAG, "Switched to Connected Device tab");
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void checkAndRequestPermissions() {
        // Request storage permission for older devices
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_STORAGE);
        }

        // Request Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        },
                        PERMISSION_REQUEST_BLUETOOTH);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            case PERMISSION_REQUEST_BLUETOOTH:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            drawerLayout.openDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the current fragment when returning to MainActivity
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof DevicesFragment) {
            ((DevicesFragment) currentFragment).refresh();
            Log.d(TAG, "onResume: Refreshed DevicesFragment");
        } else if (currentFragment instanceof ConnectedDevicesFragment) {
            ((ConnectedDevicesFragment) currentFragment).refresh();
            Log.d(TAG, "onResume: Refreshed ConnectedDevicesFragment");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the BluetoothConnectionTracker receiver
        connectionTracker.unregisterReceiver();
    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
//            String deviceAddress = getIntent().getStringExtra("device");
//            Intent intent = new Intent(this, FloatingWindowService.class);
//            intent.putExtra("deviceAddress", deviceAddress);
//            startService(intent);
//        }
//    }
}
