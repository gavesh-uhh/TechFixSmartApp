package com.techfix.app.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.techfix.app.database.BranchDAO;
import com.techfix.app.models.Branch;

/**
 * Resolves the device's coarse location and picks the nearest TechFix branch.
 * Fail-safe everywhere: any denial/error just reports null so callers keep defaults.
 */
public final class NearestBranch {

    public interface Callback {
        /** Called with the nearest branch name and distance in km, or (null, 0) when unavailable. */
        void onResult(String branchName, double km);
    }

    private NearestBranch() { }

    public static boolean hasPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void resolve(Activity activity, com.techfix.app.database.DatabaseHelper dbHelper, Callback callback) {
        if (!hasPermission(activity)) {
            callback.onResult(null, 0);
            return;
        }
        try {
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(activity);
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            deliver(activity, dbHelper, location, callback);
                        } else {
                            client.getLastLocation().addOnSuccessListener(last -> {
                                if (last != null) {
                                    deliver(activity, dbHelper, last, callback);
                                } else {
                                    callback.onResult(null, 0);
                                }
                            }).addOnFailureListener(e -> callback.onResult(null, 0));
                        }
                    })
                    .addOnFailureListener(e -> callback.onResult(null, 0));
        } catch (Exception e) {
            callback.onResult(null, 0);
        }
    }

    private static void deliver(Activity activity, com.techfix.app.database.DatabaseHelper dbHelper, Location location, Callback callback) {
        String best = null;
        double bestKm = Double.MAX_VALUE;
        for (Branch b : new BranchDAO(dbHelper).branches()) {
            double km = haversineKm(location.getLatitude(), location.getLongitude(), b.latitude, b.longitude);
            if (km < bestKm) {
                bestKm = km;
                best = b.name;
            }
        }
        if (best != null) {
            callback.onResult(best, bestKm);
        } else {
            callback.onResult(null, 0);
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}