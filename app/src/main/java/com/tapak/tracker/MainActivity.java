package com.tapak.tracker;

import android.Manifest;
import android.content.Intent;
import android.app.AlertDialog;
import android.os.SystemClock;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int REQ_LOCATION = 101;
    private static final double WEEKLY_GOAL_KM = 20.0;

    private MapView mapView;
    private Polyline routePolyline;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;

    private TextView tvGpsStatus, tvDuration, tvDistance, tvPace, tvCalories, tvWeeklyTotal, tvWeeklyStreak;
    private ProgressBar pbWeeklyGoal;
    private Button btnStartPause, btnFinish, btnTabRecord, btnTabFeed;
    private LinearLayout layoutRecord, layoutFeed;
    private RadioGroup rgSportType;
    private RecyclerView rvFeed;

    private boolean isTracking = false;
    private long accumulatedElapsedMs = 0L;
    private long segmentStartRealtimeMs = 0L;
    private double totalDistanceMeters = 0.0;
    private Location lastLocation = null;
    private final List<GeoPoint> routePoints = new ArrayList<>();

    private final Handler timerHandler = new Handler();
    private List<ActivityModel> activityList = new ArrayList<>();
    private ActivityAdapter adapter;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTracking) {
                updateLiveStatsUI();
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    private long getCurrentElapsedSeconds() {
        long totalMs = accumulatedElapsedMs;
        if (isTracking && segmentStartRealtimeMs > 0L) {
            totalMs += Math.max(0L, SystemClock.elapsedRealtime() - segmentStartRealtimeMs);
        }
        return totalMs / 1000L;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);
        new LiveUpdateManager(this).checkForUpdate();

        initViews();
        setupMap();
        loadActivities();
        setupListeners();
        checkLocationPermission();
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);
        tvDuration = findViewById(R.id.tvDuration);
        tvDistance = findViewById(R.id.tvDistance);
        tvPace = findViewById(R.id.tvPace);
        tvCalories = findViewById(R.id.tvCalories);
        tvWeeklyTotal = findViewById(R.id.tvWeeklyTotal);
        tvWeeklyStreak = findViewById(R.id.tvWeeklyStreak);
        pbWeeklyGoal = findViewById(R.id.pbWeeklyGoal);

        btnStartPause = findViewById(R.id.btnStartPause);
        btnFinish = findViewById(R.id.btnFinish);
        btnTabRecord = findViewById(R.id.btnTabRecord);
        btnTabFeed = findViewById(R.id.btnTabFeed);

        layoutRecord = findViewById(R.id.layoutRecord);
        layoutFeed = findViewById(R.id.layoutFeed);
        rgSportType = findViewById(R.id.rgSportType);
        rvFeed = findViewById(R.id.rvFeed);
    }

    private void logoutToLogin() {
    new FirebaseAuthHelper(this).signOut();
    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    finish();
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(17.0);

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        mapView.getOverlays().add(myLocationOverlay);

        routePolyline = new Polyline();
        routePolyline.getOutlinePaint().setColor(Color.parseColor("#FC4C02"));
        routePolyline.getOutlinePaint().setStrokeWidth(12f);
        mapView.getOverlays().add(routePolyline);
    }

    private void setupListeners() {
        btnStartPause.setOnClickListener(v -> {
            if (!isTracking) {
                isTracking = true;
                btnStartPause.setText("JEDA");
                btnStartPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFA000")));
                btnFinish.setVisibility(View.VISIBLE);
                timerHandler.postDelayed(timerRunnable, 1000);
            } else {
                isTracking = false;
                btnStartPause.setText("LANJUT");
                btnStartPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FC4C02")));
                timerHandler.removeCallbacks(timerRunnable);
            }
        });

        btnFinish.setOnClickListener(v -> showSaveDialog());

        btnTabRecord.setOnClickListener(v -> {
            layoutRecord.setVisibility(View.VISIBLE);
            layoutFeed.setVisibility(View.GONE);
            btnTabRecord.setTextColor(Color.parseColor("#FC4C02"));
            btnTabFeed.setTextColor(Color.parseColor("#757575"));
        });

        btnTabFeed.setOnClickListener(v -> {
            layoutRecord.setVisibility(View.GONE);
            layoutFeed.setVisibility(View.VISIBLE);
            btnTabFeed.setTextColor(Color.parseColor("#FC4C02"));
            btnTabRecord.setTextColor(Color.parseColor("#757575"));
            updateWeeklyGoalUI();
        });
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 3, this);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        tvGpsStatus.setText("GPS: Siap (" + (int) location.getAccuracy() + "m)");
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (isTracking) {
            if (lastLocation != null) {
                float dist = lastLocation.distanceTo(location);
                if (dist > 1.5f) {
                    totalDistanceMeters += dist;
                    routePoints.add(point);
                    routePolyline.setPoints(routePoints);
                    mapView.invalidate();
                }
            } else {
                routePoints.add(point);
            }
            lastLocation = location;
            updateLiveStatsUI();
        }
        mapView.getController().animateTo(point);
    }

    private void updateLiveStatsUI() {
        long hrs = elapsedSeconds / 3600;
        long mins = (elapsedSeconds % 3600) / 60;
        long secs = elapsedSeconds % 60;
        tvDuration.setText(String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs));

        double distKm = totalDistanceMeters / 1000.0;
        tvDistance.setText(String.format(Locale.US, "%.2f", distKm));

        if (distKm > 0.05) {
            double paceMinPerKm = (elapsedSeconds / 60.0) / distKm;
            int pMin = (int) paceMinPerKm;
            int pSec = (int) ((paceMinPerKm - pMin) * 60);
            tvPace.setText(String.format(Locale.US, "%d'%02d\"", pMin, pSec));
        }

        int calories = calculateCalories(distKm);
        tvCalories.setText(String.valueOf(calories));
    }

    private int calculateCalories(double distKm) {
        int checkedId = rgSportType.getCheckedRadioButtonId();
        if (checkedId == R.id.rbRide) return (int) (distKm * 32);
        if (checkedId == R.id.rbWalk) return (int) (distKm * 45);
        return (int) (distKm * 65);
    }

    private String getSelectedSportName() {
        int checkedId = rgSportType.getCheckedRadioButtonId();
        if (checkedId == R.id.rbRide) return "Gowes";
        if (checkedId == R.id.rbWalk) return "Jalan";
        return "Lari";
    }

    private void showSaveDialog() {
        isTracking = false;
        timerHandler.removeCallbacks(timerRunnable);

        EditText inputTitle = new EditText(this);
        inputTitle.setHint("Contoh: Lari Pagi Alun-Alun");

        new AlertDialog.Builder(this)
                .setTitle("Simpan Aktivitas")
                .setMessage("Beri judul aktivitas kerenmu:")
                .setView(inputTitle)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String title = inputTitle.getText().toString().trim();
                    if (title.isEmpty()) title = getSelectedSportName() + " Santai";

                    double distKm = totalDistanceMeters / 1000.0;
                    long nowMs = System.currentTimeMillis();
                    String dateStr = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(nowMs));

                    List<ActivityModel.RoutePoint> savedPoints = new ArrayList<>();
                    for (GeoPoint gp : routePoints) {
                        savedPoints.add(new ActivityModel.RoutePoint(gp.getLatitude(), gp.getLongitude()));
                    }

                    ActivityModel newAct = new ActivityModel(
                            String.valueOf(nowMs),
                            title,
                            getSelectedSportName(),
                            dateStr,
                            nowMs,
                            distKm,
                            tvDuration.getText().toString(),
                            tvPace.getText().toString(),
                            calculateCalories(distKm),
                            savedPoints
                    );

                    activityList.add(0, newAct);
                    saveActivities();
                    adapter.notifyDataSetChanged();
                    resetTracker();
                    btnTabFeed.performClick();
                    Toast.makeText(this, "Aktivitas tersimpan di Linimasa!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Lanjut Rekam", (dialog, which) -> {
                    isTracking = true;
                    timerHandler.postDelayed(timerRunnable, 1000);
                })
                .setNeutralButton("Buang", (dialog, which) -> resetTracker())
                .show();
    }

    private void showDownloadFormatDialog(ActivityModel item) {
        String[] options = {"Kartu Peta PNG (Siap Upload ke Story)", "Jalur Rute GPX (File Koordinat)"};
        new AlertDialog.Builder(this)
                .setTitle("Unduh Hasil Aktivitas")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        exportActivityAsPng(item);
                    } else {
                        exportActivityAsGpx(item);
                    }
                })
                .show();
    }

    private void exportActivityAsPng(ActivityModel item) {
        int width = 1080;
        int height = 1350;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor("#14171C"));

        Paint brandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.parseColor("#FC4C02"));
        brandPaint.setTextSize(54f);
        brandPaint.setFakeBoldText(true);
        canvas.drawText("TAPAK.", 72f, 120f, brandPaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(46f);
        titlePaint.setFakeBoldText(true);
        canvas.drawText(item.title, 72f, 195f, titlePaint);

        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#B0B7C3"));
        subPaint.setTextSize(30f);
        canvas.drawText(item.sportType + " • " + item.date, 72f, 245f, subPaint);

        float mapLeft = 72f, mapTop = 290f, mapRight = width - 72f, mapBottom = 910f;
        Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(Color.parseColor("#1E232B"));
        canvas.drawRoundRect(mapLeft, mapTop, mapRight, mapBottom, 32f, 32f, boxPaint);

        if (item.routePoints != null && item.routePoints.size() >= 2) {
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (ActivityModel.RoutePoint p : item.routePoints) {
                if (p.lat < minLat) minLat = p.lat;
                if (p.lat > maxLat) maxLat = p.lat;
                if (p.lon < minLon) minLon = p.lon;
                if (p.lon > maxLon) maxLon = p.lon;
            }
            double spanLat = Math.max(maxLat - minLat, 0.0005);
            double spanLon = Math.max(maxLon - minLon, 0.0005);

            Path path = new Path();
            float pad = 80f;
            float drawW = (mapRight - mapLeft) - (pad * 2);
            float drawH = (mapBottom - mapTop) - (pad * 2);

            for (int i = 0; i < item.routePoints.size(); i++) {
                ActivityModel.RoutePoint p = item.routePoints.get(i);
                float nx = (float) ((p.lon - minLon) / spanLon);
                float ny = (float) (1.0 - ((p.lat - minLat) / spanLat));
                float x = mapLeft + pad + (nx * drawW);
                float y = mapTop + pad + (ny * drawH);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }

            Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            routePaint.setColor(Color.parseColor("#FC4C02"));
            routePaint.setStyle(Paint.Style.STROKE);
            routePaint.setStrokeWidth(12f);
            routePaint.setStrokeCap(Paint.Cap.ROUND);
            routePaint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(path, routePaint);
        } else {
            Paint noRoutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            noRoutePaint.setColor(Color.parseColor("#8A93A2"));
            noRoutePaint.setTextSize(32f);
            noRoutePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Jejak rute tersimpan untuk sesi ini", (mapLeft + mapRight) / 2f, (mapTop + mapBottom) / 2f, noRoutePaint);
        }

        Paint statLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        statLabel.setColor(Color.parseColor("#9AA2B1"));
        statLabel.setTextSize(28f);

        Paint statVal = new Paint(Paint.ANTI_ALIAS_FLAG);
        statVal.setColor(Color.WHITE);
        statVal.setTextSize(42f);
        statVal.setFakeBoldText(true);

        canvas.drawText("JARAK", 72f, 1000f, statLabel);
        canvas.drawText(String.format(Locale.US, "%.2f km", item.distanceKm), 72f, 1055f, statVal);

        canvas.drawText("PACE", 420f, 1000f, statLabel);
        canvas.drawText(item.paceStr, 420f, 1055f, statVal);

        canvas.drawText("WAKTU", 760f, 1000f, statLabel);
        canvas.drawText(item.durationStr, 760f, 1055f, statVal);

        String fileName = "TAPAK_" + item.id + ".png";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TAPAK");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    Toast.makeText(this, "PNG tersimpan di Galeri (Pictures/TAPAK/" + fileName + ")", Toast.LENGTH_LONG).show();
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TAPAK");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                Toast.makeText(this, "PNG tersimpan di " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan PNG: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportActivityAsGpx(ActivityModel item) {
        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"TAPAK\">\n  <trk>\n    <name>").append(item.title).append("</name>\n    <trkseg>\n");
        if (item.routePoints != null) {
            for (ActivityModel.RoutePoint p : item.routePoints) {
                gpx.append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\"></trkpt>\n");
            }
        }
        gpx.append("    </trkseg>\n  </trk>\n</gpx>");

        String fileName = "TAPAK_" + item.id + ".gpx";
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TAPAK");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(gpx.toString().getBytes());
            }
            Toast.makeText(this, "GPX tersimpan di " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan GPX: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int calculateWeeklyStreak() {
        if (activityList == null || activityList.isEmpty()) return 0;

        Set<String> activeWeeks = new HashSet<>();
        for (ActivityModel act : activityList) {
            long timeMs = act.timestampMs > 0 ? act.timestampMs : System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.setFirstDayOfWeek(Calendar.MONDAY);
            cal.setTimeInMillis(timeMs);
            int year = cal.get(Calendar.YEAR);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            activeWeeks.add(year + "-" + week);
        }

        Calendar cursor = Calendar.getInstance();
        cursor.setFirstDayOfWeek(Calendar.MONDAY);
        String currentKey = cursor.get(Calendar.YEAR) + "-" + cursor.get(Calendar.WEEK_OF_YEAR);

        if (!activeWeeks.contains(currentKey)) {
            cursor.add(Calendar.WEEK_OF_YEAR, -1);
            String prevKey = cursor.get(Calendar.YEAR) + "-" + cursor.get(Calendar.WEEK_OF_YEAR);
            if (!activeWeeks.contains(prevKey)) {
                return 0;
            }
        }

        int streak = 0;
        while (true) {
            String key = cursor.get(Calendar.YEAR) + "-" + cursor.get(Calendar.WEEK_OF_YEAR);
            if (activeWeeks.contains(key)) {
                streak++;
                cursor.add(Calendar.WEEK_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    private void resetTracker() {
        isTracking = false;
        elapsedSeconds = 0;
        totalDistanceMeters = 0.0;
        lastLocation = null;
        routePoints.clear();
        routePolyline.setPoints(routePoints);
        mapView.invalidate();

        tvDuration.setText("00:00:00");
        tvDistance.setText("0.00");
        tvPace.setText("0'00\"");
        tvCalories.setText("0");
        btnStartPause.setText("MULAI");
        btnStartPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FC4C02")));
        btnFinish.setVisibility(View.GONE);
    }

    private void saveActivities() {
        SharedPreferences prefs = getSharedPreferences("TAPAK_PREFS", MODE_PRIVATE);
        prefs.edit().putString("activities", new Gson().toJson(activityList)).apply();
    }

    private void loadActivities() {
        SharedPreferences prefs = getSharedPreferences("TAPAK_PREFS", MODE_PRIVATE);
        String json = prefs.getString("activities", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<ActivityModel>>() {}.getType();
            activityList = new Gson().fromJson(json, type);
        }

        adapter = new ActivityAdapter(activityList, new ActivityAdapter.OnActivityActionListener() {
            @Override
            public void onKudosClick(int position) {
                activityList.get(position).kudosCount++;
                saveActivities();
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onDownloadClick(int position) {
                showDownloadFormatDialog(activityList.get(position));
            }

            @Override
            public void onDeleteClick(int position) {
                activityList.remove(position);
                saveActivities();
                adapter.notifyItemRemoved(position);
                updateWeeklyGoalUI();
            }
        });

        rvFeed.setLayoutManager(new LinearLayoutManager(this));
        rvFeed.setAdapter(adapter);
        updateWeeklyGoalUI();
    }

    private void updateWeeklyGoalUI() {
        double totalKm = 0;
        for (ActivityModel m : activityList) totalKm += m.distanceKm;
        tvWeeklyTotal.setText(String.format(Locale.US, "%.2f km / %.1f km", totalKm, WEEKLY_GOAL_KM));
        int progress = (int) ((totalKm / WEEKLY_GOAL_KM) * 100);
        pbWeeklyGoal.setProgress(Math.min(progress, 100));

        int streak = calculateWeeklyStreak();
        tvWeeklyStreak.setText("🔥 Weekly Streak: " + streak + (streak == 1 ? " Minggu" : " Minggu"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
