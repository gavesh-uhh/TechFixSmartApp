package com.techfix.app.models;

public class SampleRepair {
    public final long id;
    public final String title, service, imageUri;

    public SampleRepair(long id, String title, String service, String imageUri) {
        this.id = id; this.title = title; this.service = service; this.imageUri = imageUri == null ? "" : imageUri;
    }
}
