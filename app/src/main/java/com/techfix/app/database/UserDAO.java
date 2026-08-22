package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.User;
import com.techfix.app.models.UserRole;

public class UserDAO {
    private final DatabaseHelper helper;

    public UserDAO(DatabaseHelper helper) { this.helper = helper; }

    public User get(long id) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,email,role FROM users WHERE id=?", new String[]{String.valueOf(id)});
        User u = c.moveToFirst() ? read(c) : null;
        c.close();
        return u;
    }

    public User findByEmail(String email) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,email,role FROM users WHERE email=?", new String[]{email});
        User u = c.moveToFirst() ? read(c) : null;
        c.close();
        return u;
    }

    public boolean authenticate(String email, String password) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id FROM users WHERE email=? AND password=?",
                new String[]{email, DatabaseHelper.hash(password)});
        boolean ok = c.moveToFirst();
        c.close();
        return ok;
    }

    public boolean create(String name, String email, String password) {
        if (name.trim().isEmpty() || email.trim().isEmpty() || password.length() < 4) return false;
        ContentValues v = new ContentValues();
        v.put("name", name.trim()); v.put("email", email.trim());
        v.put("password", DatabaseHelper.hash(password)); v.put("role", UserRole.CUSTOMER.name());
        return helper.getWritableDatabase().insertWithOnConflict("users", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE) > 0;
    }

    private static User read(Cursor c) {
        UserRole role;
        try { role = UserRole.valueOf(c.getString(3)); } catch (Exception e) { role = UserRole.CUSTOMER; }
        return new User(c.getLong(0), c.getString(1), c.getString(2), role);
    }
}
