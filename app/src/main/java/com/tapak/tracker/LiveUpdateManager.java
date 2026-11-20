
package com.tapak.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LiveUpdateManager {

    private static final String UPDATE_JSON_URL = "https://contoh-url-kamu/tapak-version.json";

    private final Activity activity;

    public LiveUpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void checkForUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_JSON_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream())
                    );
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    int latestVersionCode = json.optInt("versionCode", 1);
                    String latestVersionName = json.optString("versionName", "1.0");
                    String changelog = json.optString("changelog", "Pembaruan fitur tersedia.");
                    String apkUrl = json.optString("apkUrl", "");

                    int currentVersionCode = getCurrentVersionCode();
                    if (latestVersionCode > currentVersionCode && !apkUrl.isEmpty()) {
                        activity.runOnUiThread(() -> showUpdateDialog(latestVersionName, changelog, apkUrl));
                    }
                }
            } catch (Exception ignored) {
                // Diabaikan jika tidak ada koneksi internet atau server belum dapat dihubungi
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private int getCurrentVersionCode() {
        try {
            PackageInfo packageInfo = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) packageInfo.getLongVersionCode();
            } else {
                return packageInfo.versionCode;
            }
        } catch (Exception e) {
            return 1;
        }
    }

    private void showUpdateDialog(String versionName, String changelog, String apkUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Live Update TAPAK v" + versionName)
                .setMessage(changelog)
                .setCancelable(false)
                .setPositiveButton("Update Sekarang", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    activity.startActivity(intent);
                })
                .setNegativeButton("Nanti Saja", null)
                .show();
    }
}
