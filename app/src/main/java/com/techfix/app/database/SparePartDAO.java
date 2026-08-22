package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.SparePart;
import java.util.ArrayList;
import java.util.List;

public class SparePartDAO {
    private final DatabaseHelper helper;

    public SparePartDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<SparePart> all() {
        return allByBranch("All Branches");
    }

    public List<SparePart> allByBranch(String branch) {
        List<SparePart> list = new ArrayList<>();
        String sql = (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch))
                ? "SELECT id, name, quantity, branch FROM parts WHERE branch=? ORDER BY name ASC"
                : "SELECT id, name, quantity, branch FROM parts ORDER BY branch, name ASC";
        String[] args = (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch))
                ? new String[]{branch} : null;

        Cursor c = helper.getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            list.add(new SparePart(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3)));
        }
        c.close();
        return list;
    }

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

    public boolean updateQuantity(long id, int newQuantity) {
        int qty = Math.max(0, newQuantity);
        ContentValues v = new ContentValues();
        v.put("quantity", qty);
        return helper.getWritableDatabase().update("parts", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean adjustStock(long id, int delta) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT quantity FROM parts WHERE id=?", new String[]{String.valueOf(id)});
        if (c.moveToFirst()) {
            int cur = c.getInt(0);
            c.close();
            return updateQuantity(id, cur + delta);
        }
        c.close();
        return false;
    }

    public boolean add(String name, String branch, int quantity) {
        if (name == null || name.trim().isEmpty()) return false;
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("branch", (branch != null && !branch.trim().isEmpty()) ? branch.trim() : "Colombo branch");
        v.put("quantity", Math.max(0, quantity));
        return helper.getWritableDatabase().insert("parts", null, v) > 0;
    }

    public boolean delete(long id) {
        return helper.getWritableDatabase().delete("parts", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public int getLowStockCount(int threshold) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM parts WHERE quantity <= ?", new String[]{String.valueOf(threshold)});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }
}
