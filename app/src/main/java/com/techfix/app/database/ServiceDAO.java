package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.Service;
import java.util.ArrayList;
import java.util.List;

public class ServiceDAO {
    private final DatabaseHelper helper;

    public ServiceDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<Service> list() {
        return listByBranch("All Branches");
    }

    public List<Service> listByBranch(String branch) {
        List<Service> out = new ArrayList<>();
        String sql;
        String[] args;

        if (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch)) {
            sql = "SELECT id, name, category, price, requiredPart, branch FROM services WHERE branch=? OR branch='All Branches' ORDER BY category, name";
            args = new String[]{branch};
        } else {
            sql = "SELECT id, name, category, price, requiredPart, branch FROM services ORDER BY branch, category, name";
            args = null;
        }

        Cursor c = helper.getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            out.add(new Service(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5)));
        }
        c.close();
        return out;
    }

    public List<String> searchByBranch(String query, String branch) {
        List<String> out = new ArrayList<>();
        String sql;
        String[] args;

        if (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch)) {
            sql = "SELECT name||' · Rs '||price FROM services WHERE (name LIKE ? OR category LIKE ?) AND (branch=? OR branch='All Branches') ORDER BY name";
            args = new String[]{"%" + query + "%", "%" + query + "%", branch};
        } else {
            sql = "SELECT name||' · Rs '||price FROM services WHERE name LIKE ? OR category LIKE ? ORDER BY name";
            args = new String[]{"%" + query + "%", "%" + query + "%"};
        }

        Cursor c = helper.getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    public List<String> all() { return allByBranch("All Branches"); }

    public List<String> allByBranch(String branch) {
        return searchByBranch("", branch);
    }

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

    public boolean add(String name, String category, double price, String requiredPart, String branch) {
        if (name == null || name.trim().isEmpty() || price <= 0) return false;
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("category", (category != null && !category.trim().isEmpty()) ? category.trim() : "Mobile phone");
        v.put("price", price);
        v.put("requiredPart", requiredPart != null ? requiredPart.trim() : "");
        v.put("branch", (branch != null && !branch.trim().isEmpty()) ? branch.trim() : "All Branches");
        return helper.getWritableDatabase().insertWithOnConflict("services", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE) > 0;
    }

    public boolean delete(long id) {
        return helper.getWritableDatabase().delete("services", "id=?", new String[]{String.valueOf(id)}) > 0;
    }
}
