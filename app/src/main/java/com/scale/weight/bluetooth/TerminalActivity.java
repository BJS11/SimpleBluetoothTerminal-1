package com.scale.weight.bluetooth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

public class TerminalActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        // Set up the Toolbar and enable the back button
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            String deviceAddress = getIntent().getStringExtra("device");
            Fragment fragment = new TerminalFragment();
            Bundle args = new Bundle();
            args.putString("device", deviceAddress);
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.terminal_fragment_container, fragment, "terminal")
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
//            String deviceAddress = getIntent().getStringExtra("device");
//            Log.d("DEBUGGGG", "DEVICEDDD: "+deviceAddress);
//            Intent intent = new Intent(this, FloatingWindowService.class);
//            intent.putExtra("deviceAddress", deviceAddress);
//            startService(intent);
//        }
//    }
}