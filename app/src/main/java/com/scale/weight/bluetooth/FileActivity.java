package com.scale.weight.bluetooth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

public class FileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            Fragment fragment = new FileFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.file_fragment_container, fragment, "file")
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        // Stop FloatingWindowService to remove the floating window when the app is in the foreground
//        stopService(new Intent(this, FloatingWindowService.class));
//    }
//
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