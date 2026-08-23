package com.techfix.app.models;

public class RepairStatus {
    public final long id;
    public final long appointmentId;
    public final String status, updatedAt, note;

    public RepairStatus(long id, long appointmentId, String status, String updatedAt, String note) {
        this.id = id; this.appointmentId = appointmentId; this.status = status;
        this.updatedAt = updatedAt; this.note = note == null ? "" : note;
    }
}
