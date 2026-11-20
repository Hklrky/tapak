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

    private static final String GITHUB_UPDATE_JSON_URL =
            "https://raw.githubusercontent.com/Hklrky/tapak/main/update.json";

    private final Activity activity;

    public LiveUpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void checkForUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(GITHUB_UPDATE_JSON_URL + "?t=" + System.currentTimeMillis());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream())
                    );
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    int latestVersionCode = json.optInt("versionCode", 1);
                    String latestVersionName = json.optString("versionName", "1.0");
                    String changelog = json.optString("changelog", "Versi baru TAPAK tersedia.");
                    String apkUrl = json.optString("apkUrl", "");

                    if (latestVersionCode > getCurrentVersionCode() && !apkUrl.isEmpty()) {
                        activity.runOnUiThread(() -> showUpdatePopup(latestVersionName, changelog, apkUrl));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private int getCurrentVersionCode() {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    private void showUpdatePopup(String versionName, String changelog, String apkUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        new AlertDialog.Builder(activity)
                .setTitle("Update Tersedia: TAPAK v" + versionName)
                .setMessage("Catatan pembaruan:\n" + changelog + "\n\nUnduh file APK terbaru sekarang?")
                .setCancelable(false)
                .setPositiveButton("Download APK", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    activity.startActivity(intent);
                })
                .setNegativeButton("Nanti", null)
                .show();
    }
}
