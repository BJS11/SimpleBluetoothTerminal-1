package com.scale.weight.bluetooth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class InfoFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_info, container, false);
        TextView infoText = view.findViewById(R.id.info_text);
        infoText.setText("Bluetooth Weight Scale App\nVersion: 1.0\n\nThis app connects to Bluetooth weight scales to read and log weight measurements.");
        return view;
    }
}