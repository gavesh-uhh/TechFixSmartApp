package com.techfix.app.database;

import android.content.ContentValues;
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

    /** Branch names only — single source of truth for dropdowns. */
    public String[] namesArray() {
        List<Branch> list = branches();
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i).name;
        return out;
    }

    /** {"All Branches", ...names} for filter spinners. */
    public String[] namesArrayWithAll() {
        List<Branch> list = branches();
        String[] out = new String[list.size() + 1];
        out[0] = "All Branches";
        for (int i = 0; i < list.size(); i++) out[i + 1] = list.get(i).name;
        return out;
    }

    public String firstName() {
        List<Branch> list = branches();
        return list.isEmpty() ? "Colombo branch" : list.get(0).name;
    }

    /** Picks the nearest branch that has a technician with the right skill and (if needed) the spare part in stock. */
    public String nearestFor(String device, String service, double latitude, double longitude) {
        String skill = device.contains("Laptop") ? "Laptop / computer" : "Mobile phone";
        String part = new ServiceDAO(helper).requiredPart(service);
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT b.name,b.latitude,b.longitude FROM branches b WHERE EXISTS(SELECT 1 FROM technicians t WHERE t.branch=b.name AND t.skill=? AND t.available=1) AND ( ?='' OR EXISTS(SELECT 1 FROM parts p WHERE p.branch=b.name AND p.name=? AND p.quantity>0))",
                new String[]{skill, part, part});
        String best = "Colombo branch";
        double distance = Double.MAX_VALUE;
        while (c.moveToNext()) {
            double d = Math.hypot(c.getDouble(1) - latitude, c.getDouble(2) - longitude);
            if (d < distance) { distance = d; best = c.getString(0); }
        }
        c.close();
        return best;
    }
}
