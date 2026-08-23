package com.techfix.app.models;

public class Technician {
    public final long id;
    public final String name, branch, skill;
    public final boolean available;

    public Technician(long id, String name, String branch, String skill, boolean available) {
        this.id = id; this.name = name; this.branch = branch; this.skill = skill; this.available = available;
    }
}
