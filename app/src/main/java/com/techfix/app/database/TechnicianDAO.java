package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.Technician;
import java.util.ArrayList;
import java.util.List;

public class TechnicianDAO {
    private final DatabaseHelper helper;

    public TechnicianDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<Technician> all() {
        return allByBranch("All Branches");
    }

    public List<Technician> allByBranch(String branch) {
        List<Technician> out = new ArrayList<>();
        String sql = (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch))
                ? "SELECT id,name,branch,skill,available FROM technicians WHERE branch=? ORDER BY name"
                : "SELECT id,name,branch,skill,available FROM technicians ORDER BY branch, name";
        String[] args = (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch))
                ? new String[]{branch} : null;

        Cursor c = helper.getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(new Technician(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1));
        c.close();
        return out;
    }

    public String availableFor(String branch, String device) {
        String skill = (device != null && device.contains("Laptop")) ? "Laptop / computer" : "Mobile phone";
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT name FROM technicians WHERE branch=? AND skill=? AND available=1 LIMIT 1", new String[]{branch, skill});
        String n = c.moveToFirst() ? c.getString(0) : "Technician to be assigned";
        c.close();
        return n;
    }

    public void setAvailability(String name, boolean available) {
        ContentValues v = new ContentValues();
        v.put("available", available ? 1 : 0);
        helper.getWritableDatabase().update("technicians", v, "name=?", new String[]{name});
    }

    public boolean add(String name, String branch, String skill) {
        if (name == null || name.trim().isEmpty()) return false;
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("branch", branch != null ? branch.trim() : "Colombo branch");
        v.put("skill", skill != null ? skill.trim() : "Mobile phone");
        v.put("available", 1);
        return helper.getWritableDatabase().insert("technicians", null, v) > 0;
    }

    public boolean delete(long id) {
        return helper.getWritableDatabase().delete("technicians", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public int getActiveJobCount(String techName) {
        if (techName == null || techName.isEmpty()) return 0;
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM appointments WHERE technician=? AND status!='Completed'",
                new String[]{techName});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }
}
