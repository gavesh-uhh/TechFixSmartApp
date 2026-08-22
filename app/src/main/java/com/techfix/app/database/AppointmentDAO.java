package com.techfix.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.AppointmentStatus;
import com.techfix.app.models.Payment;
import com.techfix.app.models.PaymentStatus;
import com.techfix.app.models.RepairStatus;
import java.util.ArrayList;
import java.util.List;

public class AppointmentDAO {
    private static final String COLS = "id,user_id,device,problem,branch,status,service,price,technician,payment,time_slot,created_at,photo_uri";
    private final DatabaseHelper helper;

    public AppointmentDAO(DatabaseHelper helper) { this.helper = helper; }

    public long add(long userId, String device, String problem, String branch, String service,
                    double price, String technician, String timeSlot) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("device", device);
        v.put("problem", problem);
        v.put("branch", branch);
        v.put("status", AppointmentStatus.REQUEST_RECEIVED.label);
        v.put("service", service);
        v.put("price", price);
        v.put("technician", technician);
        v.put("payment", PaymentStatus.PENDING.label);
        v.put("time_slot", timeSlot == null ? "" : timeSlot);
        v.put("created_at", DatabaseHelper.now());
        v.put("photo_uri", "");
        long id = helper.getWritableDatabase().insert("appointments", null, v);
        addHistory(id, AppointmentStatus.REQUEST_RECEIVED.label, "Appointment placed by customer");
        return id;
    }

    public List<Appointment> forUser(long userId) { return query("WHERE user_id=" + userId + " ORDER BY id DESC"); }
    public List<Appointment> all() { return query("ORDER BY id DESC"); }
    public List<Appointment> activeFor(long userId) { return query("WHERE user_id=" + userId + " AND status!='" + AppointmentStatus.COMPLETED.label + "' ORDER BY id DESC"); }
    public List<Appointment> historyFor(long userId) { return query("WHERE user_id=" + userId + " AND status='" + AppointmentStatus.COMPLETED.label + "' ORDER BY id DESC"); }

    public Appointment get(long id) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT " + COLS + " FROM appointments WHERE id=?", new String[]{String.valueOf(id)});
        Appointment a = c.moveToFirst() ? read(c) : null;
        c.close();
        return a;
    }

    public void updateStatus(long id, String status) {
        ContentValues v = new ContentValues(); v.put("status", status);
        helper.getWritableDatabase().update("appointments", v, "id=?", new String[]{String.valueOf(id)});
        addHistory(id, status, "Status updated by staff");
    }

    public void updateTechnician(long id, String technician) {
        ContentValues v = new ContentValues(); v.put("technician", technician);
        helper.getWritableDatabase().update("appointments", v, "id=?", new String[]{String.valueOf(id)});
        addHistory(id, get(id) != null ? get(id).status : "", "Assigned to " + technician);
    }

    public void updatePrice(long id, double price) {
        ContentValues v = new ContentValues(); v.put("price", price);
        helper.getWritableDatabase().update("appointments", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("appointments", "id=?", new String[]{String.valueOf(id)});
        helper.getWritableDatabase().delete("status_history", "appointment_id=?", new String[]{String.valueOf(id)});
    }

    public List<RepairStatus> statusHistory(long appointmentId) {
        List<RepairStatus> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT id,appointment_id,status,updated_at,note FROM status_history WHERE appointment_id=? ORDER BY id ASC",
                new String[]{String.valueOf(appointmentId)});
        while (c.moveToNext()) out.add(new RepairStatus(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4)));
        c.close();
        return out;
    }

    public boolean pay(long id, double amount, String method) {
        Appointment a = get(id);
        if (a == null || PaymentStatus.PAID.label.equals(a.payment)) return false;
        ContentValues v = new ContentValues();
        v.put("appointment_id", id); v.put("amount", amount); v.put("method", method); v.put("paid_at", DatabaseHelper.now());
        helper.getWritableDatabase().insert("payments", null, v);
        markPaid(id);
        addHistory(id, a.status, "Payment of Rs " + (long) amount + " received (" + method + ")");
        return true;
    }

    public Payment paymentFor(long appointmentId) {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT id,appointment_id,amount,method,paid_at FROM payments WHERE appointment_id=? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(appointmentId)});
        Payment p = c.moveToFirst() ? new Payment(c.getLong(0), c.getLong(1), c.getDouble(2), c.getString(3), c.getString(4)) : null;
        c.close();
        return p;
    }

    public void setPhoto(long id, String uri) {
        ContentValues v = new ContentValues(); v.put("photo_uri", uri);
        helper.getWritableDatabase().update("appointments", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void markPaid(long id) {
        ContentValues v = new ContentValues(); v.put("payment", PaymentStatus.PAID.label);
        helper.getWritableDatabase().update("appointments", v, "id=?", new String[]{String.valueOf(id)});
    }

    // ---- internals ----
    private void addHistory(long appointmentId, String status, String note) {
        ContentValues v = new ContentValues();
        v.put("appointment_id", appointmentId); v.put("status", status);
        v.put("updated_at", DatabaseHelper.now()); v.put("note", note);
        helper.getWritableDatabase().insert("status_history", null, v);
    }

    private List<Appointment> query(String clause) {
        List<Appointment> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT " + COLS + " FROM appointments " + clause, null);
        while (c.moveToNext()) out.add(read(c));
        c.close();
        return out;
    }

    private static Appointment read(Cursor c) {
        return new Appointment(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                c.getString(5), c.getString(6), c.getDouble(7), c.getString(8), c.getString(9),
                c.getString(10), c.getString(11), c.getString(12));
    }
}
