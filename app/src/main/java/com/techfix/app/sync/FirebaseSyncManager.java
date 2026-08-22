package com.techfix.app.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.Service;
import com.techfix.app.models.SparePart;
import com.techfix.app.models.Technician;
import com.techfix.app.models.User;
import com.techfix.app.util.NetworkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Offline-First Firebase Sync Manager for TechFix Smart App.
 * Automatically synchronizes SQLite database (techfix.db) with Firebase Cloud Firestore
 * whenever active internet connectivity is detected.
 */
public class FirebaseSyncManager {

    private static final String TAG = "FirebaseSyncManager";
    private static volatile FirebaseSyncManager instance;

    private final List<SyncListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private boolean isInitialized = false;

    public interface SyncListener {
        void onSyncStatusChanged(boolean isSyncing, boolean success);
    }

    public interface OnSyncCompleteListener {
        void onSyncComplete(boolean success, String message);
    }

    public static FirebaseSyncManager getInstance() {
        if (instance == null) {
            synchronized (FirebaseSyncManager.class) {
                if (instance == null) {
                    instance = new FirebaseSyncManager();
                }
            }
        }
        return instance;
    }

    private FirebaseSyncManager() {}

    /**
     * Initializes network connectivity monitoring and triggers auto-sync on active connection.
     */
    public synchronized void init(Context context) {
        if (isInitialized || context == null) return;
        isInitialized = true;
        Context appContext = context.getApplicationContext();

        NetworkUtils.registerNetworkCallback(appContext, isOnline -> {
            if (isOnline) {
                Log.d(TAG, "Network restored: triggering automatic Firebase sync");
                sync(appContext, null);
            }
        });

        // Trigger immediate sync if already online on startup
        if (NetworkUtils.isOnline(appContext)) {
            sync(appContext, null);
        }
    }

    public void addListener(SyncListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SyncListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(boolean syncing, boolean success) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (SyncListener listener : listeners) {
                try {
                    listener.onSyncStatusChanged(syncing, success);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Performs full bidirectional sync between SQLite and Firestore.
     */
    public void sync(Context context, OnSyncCompleteListener completeListener) {
        if (context == null) {
            if (completeListener != null) completeListener.onSyncComplete(false, "Context is null");
            return;
        }

        Context appContext = context.getApplicationContext();

        if (!NetworkUtils.isOnline(appContext)) {
            Log.d(TAG, "Device is offline. Skipping Firebase cloud sync.");
            if (completeListener != null) {
                completeListener.onSyncComplete(false, "Device offline");
            }
            return;
        }

        if (!isSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress.");
            if (completeListener != null) {
                completeListener.onSyncComplete(true, "Sync in progress");
            }
            return;
        }

        notifyListeners(true, false);

        new Thread(() -> {
            boolean success = false;
            String message = "Sync completed";
            try {
                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(appContext);

                // 1. Push SQLite data to Firestore
                pushLocalDataToFirestore(dbHelper, firestore);

                // 2. Pull Firestore data to SQLite
                pullFirestoreDataToLocal(dbHelper, firestore);

                success = true;
                Log.d(TAG, "Firebase sync completed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Firebase sync failed: " + e.getMessage(), e);
                message = "Sync failed: " + e.getMessage();
            } finally {
                isSyncing.set(false);
                notifyListeners(false, success);
                if (completeListener != null) {
                    final boolean finalSuccess = success;
                    final String finalMessage = message;
                    new Handler(Looper.getMainLooper()).post(() ->
                            completeListener.onSyncComplete(finalSuccess, finalMessage));
                }
            }
        }).start();
    }

    /**
     * Pushes local SQLite tables (appointments, parts, services, technicians, users, status_history, payments) to Firestore.
     */
    private void pushLocalDataToFirestore(DatabaseHelper dbHelper, FirebaseFirestore firestore) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Push Users
        Cursor cu = db.rawQuery("SELECT id, name, email, phone, role FROM users", null);
        while (cu.moveToNext()) {
            long id = cu.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", cu.getString(1));
            map.put("email", cu.getString(2));
            map.put("phone", cu.getString(3));
            map.put("role", cu.getString(4));
            firestore.collection("users").document("user_" + id).set(map, SetOptions.merge());
        }
        cu.close();

        // Push Appointments
        Cursor ca = db.rawQuery("SELECT id, user_id, device, problem, branch, status, service, price, technician, payment, time_slot, created_at, photo_uri FROM appointments", null);
        while (ca.moveToNext()) {
            long id = ca.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("userId", ca.getLong(1));
            map.put("device", ca.getString(2));
            map.put("problem", ca.getString(3));
            map.put("branch", ca.getString(4));
            map.put("status", ca.getString(5));
            map.put("service", ca.getString(6));
            map.put("price", ca.getDouble(7));
            map.put("technician", ca.getString(8));
            map.put("payment", ca.getString(9));
            map.put("timeSlot", ca.getString(10));
            map.put("createdAt", ca.getString(11));
            map.put("photoUri", ca.getString(12));
            firestore.collection("appointments").document("apt_" + id).set(map, SetOptions.merge());
        }
        ca.close();

        // Push Spare Parts
        Cursor cp = db.rawQuery("SELECT id, name, quantity, branch FROM parts", null);
        while (cp.moveToNext()) {
            long id = cp.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", cp.getString(1));
            map.put("quantity", cp.getInt(2));
            map.put("branch", cp.getString(3));
            firestore.collection("parts").document("part_" + id).set(map, SetOptions.merge());
        }
        cp.close();

        // Push Services
        Cursor cs = db.rawQuery("SELECT id, name, category, price, requiredPart FROM services", null);
        while (cs.moveToNext()) {
            long id = cs.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", cs.getString(1));
            map.put("category", cs.getString(2));
            map.put("price", cs.getDouble(3));
            map.put("requiredPart", cs.getString(4));
            firestore.collection("services").document("srv_" + id).set(map, SetOptions.merge());
        }
        cs.close();

        // Push Technicians
        Cursor ct = db.rawQuery("SELECT id, name, branch, skill, available FROM technicians", null);
        while (ct.moveToNext()) {
            long id = ct.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", ct.getString(1));
            map.put("branch", ct.getString(2));
            map.put("skill", ct.getString(3));
            map.put("available", ct.getInt(4) == 1);
            firestore.collection("technicians").document("tech_" + id).set(map, SetOptions.merge());
        }
        ct.close();

        // Push Status History
        Cursor ch = db.rawQuery("SELECT id, appointment_id, status, updated_at, note FROM status_history", null);
        while (ch.moveToNext()) {
            long id = ch.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("appointmentId", ch.getLong(1));
            map.put("status", ch.getString(2));
            map.put("updatedAt", ch.getString(3));
            map.put("note", ch.getString(4));
            firestore.collection("status_history").document("hist_" + id).set(map, SetOptions.merge());
        }
        ch.close();

        // Push Payments
        Cursor cpay = db.rawQuery("SELECT id, appointment_id, amount, method, paid_at FROM payments", null);
        while (cpay.moveToNext()) {
            long id = cpay.getLong(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("appointmentId", cpay.getLong(1));
            map.put("amount", cpay.getDouble(2));
            map.put("method", cpay.getString(3));
            map.put("paidAt", cpay.getString(4));
            firestore.collection("payments").document("pay_" + id).set(map, SetOptions.merge());
        }
        cpay.close();
    }

    /**
     * Pulls remote Firestore collections and updates local SQLite database.
     */
    private void pullFirestoreDataToLocal(DatabaseHelper dbHelper, FirebaseFirestore firestore) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Pull Appointments
        try {
            QuerySnapshot qs = com.google.android.gms.tasks.Tasks.await(firestore.collection("appointments").get());
            for (DocumentSnapshot doc : qs.getDocuments()) {
                Long id = doc.getLong("id");
                if (id == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("user_id", doc.getLong("userId") != null ? doc.getLong("userId") : 0L);
                v.put("device", doc.getString("device") != null ? doc.getString("device") : "");
                v.put("problem", doc.getString("problem") != null ? doc.getString("problem") : "");
                v.put("branch", doc.getString("branch") != null ? doc.getString("branch") : "");
                v.put("status", doc.getString("status") != null ? doc.getString("status") : "");
                v.put("service", doc.getString("service") != null ? doc.getString("service") : "");
                v.put("price", doc.getDouble("price") != null ? doc.getDouble("price") : 0.0);
                v.put("technician", doc.getString("technician") != null ? doc.getString("technician") : "");
                v.put("payment", doc.getString("payment") != null ? doc.getString("payment") : "");
                v.put("time_slot", doc.getString("timeSlot") != null ? doc.getString("timeSlot") : "");
                v.put("created_at", doc.getString("createdAt") != null ? doc.getString("createdAt") : "");
                v.put("photo_uri", doc.getString("photoUri") != null ? doc.getString("photoUri") : "");
                db.insertWithOnConflict("appointments", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Pull appointments skipped: " + e.getMessage());
        }

        // Pull Spare Parts
        try {
            QuerySnapshot qs = com.google.android.gms.tasks.Tasks.await(firestore.collection("parts").get());
            for (DocumentSnapshot doc : qs.getDocuments()) {
                Long id = doc.getLong("id");
                String name = doc.getString("name");
                if (id == null || name == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("name", name);
                v.put("quantity", doc.getLong("quantity") != null ? doc.getLong("quantity").intValue() : 0);
                v.put("branch", doc.getString("branch") != null ? doc.getString("branch") : "Colombo branch");
                db.insertWithOnConflict("parts", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Pull parts skipped: " + e.getMessage());
        }

        // Pull Services
        try {
            QuerySnapshot qs = com.google.android.gms.tasks.Tasks.await(firestore.collection("services").get());
            for (DocumentSnapshot doc : qs.getDocuments()) {
                Long id = doc.getLong("id");
                String name = doc.getString("name");
                if (id == null || name == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("name", name);
                v.put("category", doc.getString("category") != null ? doc.getString("category") : "Mobile phone");
                v.put("price", doc.getDouble("price") != null ? doc.getDouble("price") : 0.0);
                v.put("requiredPart", doc.getString("requiredPart") != null ? doc.getString("requiredPart") : "");
                db.insertWithOnConflict("services", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Pull services skipped: " + e.getMessage());
        }

        // Pull Technicians
        try {
            QuerySnapshot qs = com.google.android.gms.tasks.Tasks.await(firestore.collection("technicians").get());
            for (DocumentSnapshot doc : qs.getDocuments()) {
                Long id = doc.getLong("id");
                String name = doc.getString("name");
                if (id == null || name == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("name", name);
                v.put("branch", doc.getString("branch") != null ? doc.getString("branch") : "Colombo branch");
                v.put("skill", doc.getString("skill") != null ? doc.getString("skill") : "Mobile phone");
                v.put("available", Boolean.TRUE.equals(doc.getBoolean("available")) ? 1 : 0);
                db.insertWithOnConflict("technicians", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Pull technicians skipped: " + e.getMessage());
        }

        // Pull Status History
        try {
            QuerySnapshot qs = com.google.android.gms.tasks.Tasks.await(firestore.collection("status_history").get());
            for (DocumentSnapshot doc : qs.getDocuments()) {
                Long id = doc.getLong("id");
                if (id == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("appointment_id", doc.getLong("appointmentId") != null ? doc.getLong("appointmentId") : 0L);
                v.put("status", doc.getString("status") != null ? doc.getString("status") : "");
                v.put("updated_at", doc.getString("updatedAt") != null ? doc.getString("updatedAt") : "");
                v.put("note", doc.getString("note") != null ? doc.getString("note") : "");
                db.insertWithOnConflict("status_history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Pull status history skipped: " + e.getMessage());
        }

        // Pull Payments
        try {
            QuerySnapshot qs = com.google.android.gms.tasks.Tasks.await(firestore.collection("payments").get());
            for (DocumentSnapshot doc : qs.getDocuments()) {
                Long id = doc.getLong("id");
                if (id == null) continue;
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("appointment_id", doc.getLong("appointmentId") != null ? doc.getLong("appointmentId") : 0L);
                v.put("amount", doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                v.put("method", doc.getString("method") != null ? doc.getString("method") : "");
                v.put("paid_at", doc.getString("paidAt") != null ? doc.getString("paidAt") : "");
                db.insertWithOnConflict("payments", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Pull payments skipped: " + e.getMessage());
        }
    }
}
