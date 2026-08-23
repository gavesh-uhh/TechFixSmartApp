package com.techfix.app.session;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.techfix.app.models.UserRole;

/**
 * Persists the logged-in user across app restarts and manages Firebase Auth session.
 */
public class SessionManager {
    private static final String PREFS = "techfix_session";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_ROLE = "role";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void start(long userId, UserRole role) {
        prefs.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_ROLE, role.name())
                .commit();
    }

    public boolean isLoggedIn() {
        return prefs != null && prefs.contains(KEY_USER_ID) && prefs.getLong(KEY_USER_ID, -1) > 0;
    }

    public long getUserId() {
        return prefs != null ? prefs.getLong(KEY_USER_ID, -1) : -1;
    }

    public UserRole getRole() {
        if (prefs == null) return UserRole.CUSTOMER;
        try {
            return UserRole.valueOf(prefs.getString(KEY_ROLE, UserRole.CUSTOMER.name()));
        } catch (Exception e) {
            return UserRole.CUSTOMER;
        }
    }

    public void logout() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth != null) {
                auth.signOut();
            }
        } catch (Exception ignored) {}
        if (prefs != null) {
            prefs.edit().remove(KEY_USER_ID).remove(KEY_ROLE).clear().commit();
        }
    }
}
