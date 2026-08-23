package com.techfix.app.util;

import android.content.Context;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;

public final class Analytics {
    private static FirebaseAnalytics instance;

    private Analytics() { }

    private static FirebaseAnalytics get(Context context) {
        if (instance == null) {
            instance = FirebaseAnalytics.getInstance(context.getApplicationContext());
        }
        return instance;
    }

    public static void log(Context context, String event) {
        log(context, event, null, null);
    }

    public static void log(Context context, String event, String paramKey, String paramValue) {
        try {
            Bundle params = new Bundle();
            if (paramKey != null && paramValue != null) {
                params.putString(paramKey, paramValue);
            }
            get(context).logEvent(event, params);
        } catch (Exception ignored) {
        }
    }
}
