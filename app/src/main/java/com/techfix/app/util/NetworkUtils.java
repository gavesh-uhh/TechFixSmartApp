package com.techfix.app.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

public class NetworkUtils {

    public interface NetworkChangeListener {
        void onNetworkChanged(boolean isOnline);
    }

    public static boolean isOnline(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            return capabilities != null && (
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            );
        } catch (Exception e) {
            return false;
        }
    }

    public static ConnectivityManager.NetworkCallback registerNetworkCallback(Context context, NetworkChangeListener listener) {
        if (context == null || listener == null) return null;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            Handler mainHandler = new Handler(Looper.getMainLooper());

            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mainHandler.post(() -> listener.onNetworkChanged(true));
                }

                @Override
                public void onLost(Network network) {
                    mainHandler.post(() -> listener.onNetworkChanged(isOnline(context)));
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    boolean online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    mainHandler.post(() -> listener.onNetworkChanged(online));
                }
            };

            cm.registerNetworkCallback(request, callback);
            return callback;
        } catch (Exception e) {
            return null;
        }
    }
}
