package com.techfix.app.models;

public class Payment {
    public final long id;
    public final long appointmentId;
    public final double amount;
    public final String method, paidAt;

    public Payment(long id, long appointmentId, double amount, String method, String paidAt) {
        this.id = id; this.appointmentId = appointmentId; this.amount = amount;
        this.method = method; this.paidAt = paidAt;
    }
}
