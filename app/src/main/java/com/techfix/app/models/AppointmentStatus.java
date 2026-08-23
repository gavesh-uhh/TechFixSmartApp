package com.techfix.app.models;

public enum AppointmentStatus {
    REQUEST_RECEIVED("Request received"),
    DIAGNOSING("Diagnosing"),
    REPAIRING("Repairing"),
    READY_FOR_COLLECTION("Ready for collection"),
    COMPLETED("Completed");

    public final String label;

    AppointmentStatus(String label) { this.label = label; }

    public static String[] labels() {
        AppointmentStatus[] values = values();
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i].label;
        return out;
    }

    public static AppointmentStatus from(String label) {
        for (AppointmentStatus s : values()) if (s.label.equals(label)) return s;
        return REQUEST_RECEIVED;
    }
}
