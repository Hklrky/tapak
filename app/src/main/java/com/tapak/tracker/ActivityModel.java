package com.tapak.tracker;

public class ActivityModel {
    public String id;
    public String title;
    public String sportType;
    public String date;
    public double distanceKm;
    public String durationStr;
    public String paceStr;
    public int calories;
    public int kudosCount;

    public ActivityModel(String id, String title, String sportType, String date,
                         double distanceKm, String durationStr, String paceStr, int calories) {
        this.id = id;
        this.title = title;
        this.sportType = sportType;
        this.date = date;
        this.distanceKm = distanceKm;
        this.durationStr = durationStr;
        this.paceStr = paceStr;
        this.calories = calories;
        this.kudosCount = 0;
    }
}
