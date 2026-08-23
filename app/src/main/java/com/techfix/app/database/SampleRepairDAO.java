package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.SampleRepair;
import java.util.ArrayList;
import java.util.List;

public class SampleRepairDAO {
    private final DatabaseHelper helper;

    public SampleRepairDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<SampleRepair> all() {
        List<SampleRepair> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,title,service,imageUri FROM samples ORDER BY id DESC", null);
        while (c.moveToNext()) out.add(new SampleRepair(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)));
        c.close();
        return out;
    }

    public long add(String title, String service, String imageUri) {
        ContentValues v = new ContentValues();
        v.put("title", title); v.put("service", service); v.put("imageUri", imageUri);
        return helper.getWritableDatabase().insert("samples", null, v);
    }

    public int delete(long id) {
        return helper.getWritableDatabase().delete("samples", "id = ?", new String[]{String.valueOf(id)});
    }
}
