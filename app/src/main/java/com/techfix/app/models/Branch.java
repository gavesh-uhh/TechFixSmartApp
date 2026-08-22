package com.techfix.app.models;

public class Branch {
    public final long id;
    public final String name, city;
    public final double latitude, longitude;

    public Branch(long id, String name, String city, double latitude, double longitude) {
        this.id = id; this.name = name; this.city = city;
        this.latitude = latitude; this.longitude = longitude;
    }
}

