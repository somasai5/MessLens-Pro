package com.messlens.model;

/** One meal on the weekly menu of one mess type. */
public class MenuEntry {
    private final String messType;
    private final int dayNo;
    private final String meal;
    private final String items;

    public MenuEntry(String messType, int dayNo, String meal, String items) {
        this.messType = messType;
        this.dayNo = dayNo;
        this.meal = meal;
        this.items = items;
    }

    public String getMessType() { return messType; }
    public int getDayNo() { return dayNo; }
    public String getMeal() { return meal; }
    public String getItems() { return items; }
}
