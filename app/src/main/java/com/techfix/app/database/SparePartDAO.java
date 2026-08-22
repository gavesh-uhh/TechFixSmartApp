package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;

public class SparePartDAO {
    private final DatabaseHelper helper;

    public SparePartDAO(DatabaseHelper helper) { this.helper = helper; }

    public int quantity(String part, String branch) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT quantity FROM parts WHERE name=? AND branch=?", new String[]{part, branch});
        int q = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return q;
    }

    /** Takes one unit of stock for a repair. Returns false when the part is unknown or out of stock. */
    public boolean consume(String part, String branch) {
        if (part == null || part.isEmpty()) return false;
        if (quantity(part, branch) <= 0) return false;
        helper.getWritableDatabase().execSQL("UPDATE parts SET quantity=quantity-1 WHERE name=? AND branch=?", new Object[]{part, branch});
        return true;
    }

    public void restock(String part, String branch, int amount) {
        helper.getWritableDatabase().execSQL("UPDATE parts SET quantity=quantity+? WHERE name=? AND branch=?", new Object[]{amount, part, branch});
    }
}
