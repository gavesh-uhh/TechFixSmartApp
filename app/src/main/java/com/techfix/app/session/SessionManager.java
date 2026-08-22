package com.techfix.app.session;

import android.content.Context;
import android.content.SharedPreferences;
import com.techfix.app.models.UserRole;

/** Persists the logged-in user across app restarts. */
public class SessionManager {
    private static final String PREFS = "techfix_session";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_ROLE = "role";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void start(long userId, UserRole role) {
        prefs.edit().putLong(KEY_USER_ID, userId).putString(KEY_ROLE, role.name()).apply();
    }

    public boolean isLoggedIn() { return prefs.contains(KEY_USER_ID); }

    public long getUserId() { return prefs.getLong(KEY_USER_ID, -1); }

    public UserRole getRole() {
        try { return UserRole.valueOf(prefs.getString(KEY_ROLE, UserRole.CUSTOMER.name())); }
        catch (Exception e) { return UserRole.CUSTOMER; }
    }

    public void logout() { prefs.edit().clear().apply(); }
}

