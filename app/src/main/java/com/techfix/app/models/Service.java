package com.techfix.app.models;

public class Service {
    public final long id;
    public final String name, category, requiredPart, branch;
    public final double price;

    public Service(long id, String name, String category, double price, String requiredPart, String branch) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.requiredPart = requiredPart == null ? "" : requiredPart;
        this.branch = (branch != null && !branch.trim().isEmpty()) ? branch.trim() : "All Branches";
    }

    public Service(long id, String name, String category, double price, String requiredPart) {
        this(id, name, category, price, requiredPart, "All Branches");
    }
}

