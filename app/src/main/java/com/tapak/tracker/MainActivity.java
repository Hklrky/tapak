package com.tapak.tracker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
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

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int REQ_LOCATION = 101;
    private static final double WEEKLY_GOAL_KM = 20.0;

    private MapView mapView;
    private Polyline routePolyline;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;

    private TextView tvGpsStatus, tvDuration, tvDistance, tvPace, tvCalories, tvWeeklyTotal;
    private ProgressBar pbWeeklyGoal;
    private Button btnStartPause, btnFinish, btnTabRecord, btnTabFeed;
    private LinearLayout layoutRecord, layoutFeed;
    private RadioGroup rgSportType;
    private RecyclerView rvFeed;

    private boolean isTracking = false;
    private long elapsedSeconds = 0;
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
                elapsedSeconds++;
                updateLiveStatsUI();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

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
                if (dist > 1.5f) { // Filter noise GPS diam
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
        if (checkedId == R.id.rbRide) return (int) (distKm * 32); // Gowes ~32 kkal/km
        if (checkedId == R.id.rbWalk) return (int) (distKm * 45); // Jalan ~45 kkal/km
        return (int) (distKm * 65); // Lari ~65 kkal/km
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
                    String dateStr = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());

                    ActivityModel newAct = new ActivityModel(
                            String.valueOf(System.currentTimeMillis()),
                            title,
                            getSelectedSportName(),
                            dateStr,
                            distKm,
                            tvDuration.getText().toString(),
                            tvPace.getText().toString(),
                            calculateCalories(distKm)
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
