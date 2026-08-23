package com.techfix.app.database;

import android.database.Cursor;
import com.techfix.app.models.Branch;
import java.util.ArrayList;
import java.util.List;

public class BranchDAO {
    private final DatabaseHelper helper;

    public BranchDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<Branch> branches() {
        List<Branch> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,name,city,latitude,longitude FROM branches ORDER BY id", null);
        while (c.moveToNext()) out.add(new Branch(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4)));
        c.close();
        return out;
    }

    public String[] displayNamesArray() {
        List<Branch> list = branches();
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = toDisplayName(list.get(i).name);
        }
        return out;
    }

    public String[] filterNamesArray() {
        List<Branch> list = branches();
        String[] out = new String[list.size() + 1];
        out[0] = "All Branches";
        for (int i = 0; i < list.size(); i++) {
            out[i + 1] = toDisplayName(list.get(i).name);
        }
        return out;
    }

    public static String toDbName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty() || displayName.equalsIgnoreCase("All Branches")) {
            return "All Branches";
        }
        String lower = displayName.toLowerCase();
        if (lower.contains("colombo")) {
            return "Colombo branch";
        }
        if (lower.contains("galle")) {
            return "Galle branch";
        }
        return displayName;
    }

    public static String toDisplayName(String dbName) {
        if (dbName == null || dbName.trim().isEmpty() || dbName.equalsIgnoreCase("All Branches")) {
            return "All Branches";
        }
        String lower = dbName.toLowerCase();
        if (lower.contains("colombo")) {
            return "Colombo Flagship (Colombo 04)";
        }
        if (lower.contains("galle")) {
            return "Galle Service Center (Galle Fort)";
        }
        return dbName;
    }
}
