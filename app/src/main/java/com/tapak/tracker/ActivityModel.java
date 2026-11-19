package com.tapak.tracker;

import java.util.ArrayList;
import java.util.List;

public class ActivityModel {
    public String id;
    public String title;
    public String sportType;
    public String date;
    public long timestampMs;
    public double distanceKm;
    public String durationStr;
    public String paceStr;
    public int calories;
    public int kudosCount;
    public List<RoutePoint> routePoints;

    public static class RoutePoint {
        public double lat;
        public double lon;

        public RoutePoint(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public ActivityModel(String id, String title, String sportType, String date, long timestampMs,
                         double distanceKm, String durationStr, String paceStr, int calories,
                         List<RoutePoint> routePoints) {
        this.id = id;
        this.title = title;
        this.sportType = sportType;
        this.date = date;
        this.timestampMs = timestampMs;
        this.distanceKm = distanceKm;
        this.durationStr = durationStr;
        this.paceStr = paceStr;
        this.calories = calories;
        this.kudosCount = 0;
        this.routePoints = routePoints != null ? routePoints : new ArrayList<>();
    }
}
