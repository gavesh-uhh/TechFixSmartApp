package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.User;
import com.techfix.app.models.UserRole;

/**
 * Data Access Object for Users table in SQLite.
 * Supports case-insensitive email lookup, hashed & plain password matching for smooth migration.
 */
public class UserDAO {
    private final DatabaseHelper helper;

    public UserDAO(DatabaseHelper helper) { this.helper = helper; }

    public User get(long id) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,email,role,phone FROM users WHERE id=?", new String[]{String.valueOf(id)});
        User u = c.moveToFirst() ? read(c) : null;
        c.close();
        return u;
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        String cleanEmail = email.trim().toLowerCase();
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,email,role,phone FROM users WHERE LOWER(email)=?", new String[]{cleanEmail});
        User u = c.moveToFirst() ? read(c) : null;
        c.close();
        return u;
    }

    public boolean authenticate(String email, String password) {
        if (email == null || password == null) return false;
        String cleanEmail = email.trim().toLowerCase();
        String hashedPassword = DatabaseHelper.hash(password);

        // Check against both hashed password and plain password for seamless backward compatibility
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT id FROM users WHERE LOWER(email)=? AND (password=? OR password=?)",
                new String[]{cleanEmail, hashedPassword, password});
        boolean ok = c.moveToFirst();
        c.close();
        return ok;
    }

    public boolean create(String name, String email, String phone, String password) {
        if (name == null || email == null || password == null) return false;
        String cleanName = name.trim();
        String cleanEmail = email.trim().toLowerCase();
        if (cleanName.isEmpty() || cleanEmail.isEmpty() || password.length() < 4) return false;

        ContentValues v = new ContentValues();
        v.put("name", cleanName);
        v.put("email", cleanEmail);
        v.put("phone", phone != null ? phone.trim() : "");
        v.put("password", DatabaseHelper.hash(password));
        v.put("role", UserRole.CUSTOMER.name());

        return helper.getWritableDatabase().insertWithOnConflict("users", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) > 0;
    }

    public boolean create(String name, String email, String password) {
        return create(name, email, "", password);
    }

    private static User read(Cursor c) {
        UserRole role;
        try { role = UserRole.valueOf(c.getString(3)); } catch (Exception e) { role = UserRole.CUSTOMER; }
        String phone = (c.getColumnCount() > 4 && !c.isNull(4)) ? c.getString(4) : "";
        return new User(c.getLong(0), c.getString(1), c.getString(2), phone, role);
    }
}
