package com.messlens.model;

/** Average stars and number of ratings for one meal. */
public class RatingSummary {
    private final String meal;
    private final double average;
    private final int count;

    public RatingSummary(String meal, double average, int count) {
        this.meal = meal;
        this.average = average;
        this.count = count;
    }

    public String getMeal() { return meal; }
    public double getAverage() { return average; }
    public int getCount() { return count; }
}
