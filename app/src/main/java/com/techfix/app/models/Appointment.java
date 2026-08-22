package com.techfix.app.models;

/** Repair appointment for a customer's device. */
public class Appointment {
    public final long id;
    public final long userId;
    public final String device, problem, branch, status, service, technician, payment, timeSlot, createdAt, photoUri;
    public final double price;

    public Appointment(long id, long userId, String device, String problem, String branch, String status,
                       String service, double price, String technician, String payment,
                       String timeSlot, String createdAt, String photoUri) {
        this.id = id; this.userId = userId; this.device = device; this.problem = problem; this.branch = branch;
        this.status = status; this.service = service; this.price = price; this.technician = technician;
        this.payment = payment; this.timeSlot = timeSlot == null ? "" : timeSlot;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.photoUri = photoUri == null ? "" : photoUri;
    }

    public boolean isCompleted() { return AppointmentStatus.COMPLETED.label.equals(status); }
}

