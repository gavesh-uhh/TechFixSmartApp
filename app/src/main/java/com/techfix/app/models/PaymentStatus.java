package com.techfix.app.models;

public enum PaymentStatus {
    PENDING("Pending"), PAID("Paid");

    public final String label;

    PaymentStatus(String label) { this.label = label; }
}
