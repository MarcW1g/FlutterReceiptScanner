package com.example.doc_scan;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {
  private static final String CHANNEL = "tests.mwsd.dev/documentScanner";

  @Override
  public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
    GeneratedPluginRegistrant.registerWith(flutterEngine);
    new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
        .setMethodCallHandler(
          (call, result) -> {
            if (call.method.equals("openDocumentScanner")) {
              this.openDocumentScanner();

              LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String message = intent.getStringExtra("message");
                        result.success(message);

                        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(this);
                    }
                }, new IntentFilter("text-processing-finished"));

            } else {
              result.notImplemented();
            }
          }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void openDocumentScanner() {
        if (checkPermission()) {
            this.showDocumentScanner();
        } else {
            requestPermission();
        }
    }

    private void showDocumentScanner() {
        Intent intent = new Intent(MainActivity.this, CameraView.class);
        startActivity(intent);
    }

    // Camera permission
    private static final int PERMISSION_REQUEST_CODE = 200;

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
    }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
      switch (requestCode) {
          case PERMISSION_REQUEST_CODE:
              if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                  this.showDocumentScanner();
              } else {
                  Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                      if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                              != PackageManager.PERMISSION_GRANTED) {
                          showMessageOKCancel("You need to allow access permissions",
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                              requestPermission();
                                          }
                                      }
                                  });
                      }
                  }
              }
              break;
      }
  }

  private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
      new AlertDialog.Builder(MainActivity.this)
              .setMessage(message)
              .setPositiveButton("OK", okListener)
              .setNegativeButton("Cancel", null)
              .create()
              .show();
  }
}
