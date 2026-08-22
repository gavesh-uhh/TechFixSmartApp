package com.techfix.app.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline-first catalog sync (Web Services &amp; Remote Data deliverable).
 * Pulls the service catalog from the remote endpoint and caches it in SQLite.
 * If the network is unavailable, the app keeps running from the local cache.
 */
public final class CatalogSync {
    /** Change this to the deployed TechFix API base URL. */
    public static final String SERVICES_URL = "https://api.techfix.lk/services.json";

    public interface Callback { void onResult(boolean online, int synced); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private CatalogSync() { }

    public static void sync(Context context, com.techfix.app.database.ServiceDAO serviceDAO, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        IO.execute(() -> {
            try {
                String json = RemoteService.get(SERVICES_URL);
                org.json.JSONArray array = new org.json.JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject o = array.getJSONObject(i);
                    serviceDAO.upsert(
                            o.getString("name"),
                            o.optString("category", "General"),
                            o.optDouble("price", 0),
                            o.optString("requiredPart", ""));
                }
                main.post(() -> callback.onResult(true, array.length()));
            } catch (Exception e) {
                main.post(() -> callback.onResult(false, 0));
            }
        });
    }
}

