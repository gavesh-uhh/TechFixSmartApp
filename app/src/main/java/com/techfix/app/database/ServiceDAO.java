package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

public class ServiceDAO {
    private final DatabaseHelper helper;

    public ServiceDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<String> search(String query) {
        List<String> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT name||' · Rs '||price FROM services WHERE name LIKE ? OR category LIKE ?",
                new String[]{"%" + query + "%", "%" + query + "%"});
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    public List<String> all() { return search(""); }

    public String serviceName(String item) { return item.split(" · Rs ")[0]; }

    public double price(String item) { try { return Double.parseDouble(item.split(" · Rs ")[1]); } catch (Exception e) { return 0; } }

    public String requiredPart(String service) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT requiredPart FROM services WHERE name=?", new String[]{service});
        String p = c.moveToFirst() ? c.getString(0) : "";
        c.close();
        return p;
    }

    public void updatePrice(String service, double price) {
        ContentValues v = new ContentValues(); v.put("price", price);
        helper.getWritableDatabase().update("services", v, "name=?", new String[]{service});
    }

    public void upsert(String name, String category, double price, String requiredPart) {
        ContentValues v = new ContentValues(); v.put("name", name); v.put("category", category); v.put("price", price); v.put("requiredPart", requiredPart);
        long id = helper.getWritableDatabase().insertWithOnConflict("services", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE);
        if (id < 0) helper.getWritableDatabase().update("services", v, "name=?", new String[]{name});
    }

    public boolean add(String name, String category, double price) {
        if (name.trim().isEmpty() || price <= 0) return false;
        ContentValues v = new ContentValues(); v.put("name", name.trim()); v.put("category", category); v.put("price", price); v.put("requiredPart", "");
        return helper.getWritableDatabase().insertWithOnConflict("services", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE) > 0;
    }

    public String catalog() {
        StringBuilder out = new StringBuilder("Services offered:\n");
        for (String s : all()) out.append("• ").append(s).append("\n");
        out.append("\nBranches: Colombo · Galle\nTechnicians available · Spare parts tracked");
        return out.toString();
    }
}
