package com.scale.weight.bluetooth;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.io.File;

public class FileFragment extends Fragment {
    private static final String TAG = "FileFragment";
    private File logFile;
    private ActivityResultLauncher<String> requestStoragePermissionLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestStoragePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openFileLocation();
                    } else {
                        Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file, container, false);

        TextView pathTextView = view.findViewById(R.id.path_text_view);
        Button navigateButton = view.findViewById(R.id.navigate_button);

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        logFile = new File(downloadsDir, "BT_WEIGHT_LOGS.txt");
        String filePath = logFile.getAbsolutePath();

        pathTextView.setText("\nLog File Path:\n" + filePath);

        navigateButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (!checkPermissions()) {
                    requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                    return;
                }
            }
            openFileLocation();
        });

        return view;
    }

    private boolean checkPermissions() {
        return requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openFileLocation() {
        try {

            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));

//            if (!logFile.exists()) {
//                openDownloadsFolder();
//                Toast.makeText(requireContext(), "Log file not found, opening Downloads folder",
//                        Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            // Temporary: Skip FileProvider and directly open the Downloads folder
//            Toast.makeText(requireContext(), "FileProvider skipped, opening Downloads folder",
//                    Toast.LENGTH_SHORT).show();
//            openDownloadsFolder();

        } catch (Exception e) {
            Log.e(TAG, "Error opening file: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            openDownloadsFolder();
        }
    }

//    private void openDownloadsFolder() {
//        try {
//            Uri downloadsUri;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                downloadsUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download");
//            } else {
//                downloadsUri = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
//            }
//
//            Intent intent = new Intent(Intent.ACTION_VIEW);
//            intent.setDataAndType(downloadsUri, "vnd.android.document/directory");
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
//                startActivity(intent);
//            } else {
//                Toast.makeText(requireContext(), "No file manager found to open Downloads folder",
//                        Toast.LENGTH_SHORT).show();
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error opening Downloads folder: " + e.getMessage(), e);
//            Toast.makeText(requireContext(), "Could not open Downloads folder: " + e.getMessage(),
//                    Toast.LENGTH_SHORT).show();
//        }
//    }
}