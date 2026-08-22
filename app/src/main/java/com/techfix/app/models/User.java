package com.techfix.app.models;

public class User {
    public final long id;
    public final String name, email, phone;
    public final UserRole role;

    public User(long id, String name, String email, String phone, UserRole role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone != null ? phone : "";
        this.role = role == null ? UserRole.CUSTOMER : role;
    }

    public User(long id, String name, String email, UserRole role) {
        this(id, name, email, "", role);
    }
}
