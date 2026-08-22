package com.techfix.app.database;

import android.database.Cursor;
import com.techfix.app.models.Technician;
import java.util.ArrayList;
import java.util.List;

public class TechnicianDAO {
    private final DatabaseHelper helper;

    public TechnicianDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<Technician> all() {
        List<Technician> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,branch,skill,available FROM technicians ORDER BY name", null);
        while (c.moveToNext()) out.add(new Technician(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1));
        c.close();
        return out;
    }

    public String availableFor(String branch, String device) {
        String skill = device.contains("Laptop") ? "Laptop / computer" : "Mobile phone";
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT name FROM technicians WHERE branch=? AND skill=? AND available=1 LIMIT 1", new String[]{branch, skill});
        String n = c.moveToFirst() ? c.getString(0) : "Technician to be assigned";
        c.close();
        return n;
    }

    public void setAvailability(String name, boolean available) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("available", available ? 1 : 0);
        helper.getWritableDatabase().update("technicians", v, "name=?", new String[]{name});
    }
}
