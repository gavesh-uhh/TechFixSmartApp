package com.techfix.app.models;

public class BannerItem {
    private String tag;
    private String title;
    private String subtitle;
    private int backgroundRes;
    private int tagTextColorHex;

    public BannerItem(String tag, String title, String subtitle, int backgroundRes, int tagTextColorHex) {
        this.tag = tag;
        this.title = title;
        this.subtitle = subtitle;
        this.backgroundRes = backgroundRes;
        this.tagTextColorHex = tagTextColorHex;
    }

    public String getTag() {
        return tag;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public int getBackgroundRes() {
        return backgroundRes;
    }

    public int getTagTextColorHex() {
        return tagTextColorHex;
    }
}
