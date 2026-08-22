package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.User;
import com.techfix.app.models.UserRole;
import java.util.ArrayList;
import java.util.List;

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

    public List<User> allCustomers() {
        List<User> list = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,email,role,phone FROM users WHERE role!='STAFF' ORDER BY id DESC", null);
        while (c.moveToNext()) list.add(read(c));
        c.close();
        return list;
    }

    /** Promotes/demotes a user's role (e.g. CUSTOMER -> STAFF from the Admin directory). */
    public boolean setRole(long id, String role) {
        ContentValues v = new ContentValues();
        v.put("role", role);
        return helper.getWritableDatabase().update("users", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<User> searchCustomers(String query) {
        if (query == null || query.trim().isEmpty()) return allCustomers();
        String q = "%" + query.trim().toLowerCase() + "%";
        List<User> list = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT id,name,email,role,phone FROM users WHERE role!='STAFF' AND (LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR phone LIKE ?) ORDER BY id DESC",
                new String[]{q, q, q});
        while (c.moveToNext()) list.add(read(c));
        c.close();
        return list;
    }

    public int getRepairCountForUser(long userId) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM appointments WHERE user_id=?", new String[]{String.valueOf(userId)});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    private static User read(Cursor c) {
        UserRole role;
        try { role = UserRole.valueOf(c.getString(3)); } catch (Exception e) { role = UserRole.CUSTOMER; }
        String phone = (c.getColumnCount() > 4 && !c.isNull(4)) ? c.getString(4) : "";
        return new User(c.getLong(0), c.getString(1), c.getString(2), phone, role);
    }
}
