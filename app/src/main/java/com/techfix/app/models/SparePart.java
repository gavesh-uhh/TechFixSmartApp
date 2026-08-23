package com.techfix.app.models;

public class SparePart {
    public final long id;
    public final String name, branch;
    public final int quantity;

    public SparePart(long id, String name, int quantity, String branch) {
        this.id = id; this.name = name; this.quantity = quantity; this.branch = branch;
    }
}
