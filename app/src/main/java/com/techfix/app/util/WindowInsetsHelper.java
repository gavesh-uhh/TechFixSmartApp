package com.techfix.app.util;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class WindowInsetsHelper {
    private WindowInsetsHelper() { }

    public static void apply(View header, View bottom) {
        int headerLeft=header.getPaddingLeft(), headerTop=header.getPaddingTop(), headerRight=header.getPaddingRight(), headerBottom=header.getPaddingBottom();
        int bottomLeft=bottom.getPaddingLeft(), bottomTop=bottom.getPaddingTop(), bottomRight=bottom.getPaddingRight(), bottomBottom=bottom.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(header,(view,windowInsets)->{Insets bars=windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());view.setPadding(headerLeft,headerTop+bars.top,headerRight,headerBottom);return windowInsets;});
        ViewCompat.setOnApplyWindowInsetsListener(bottom,(view,windowInsets)->{Insets bars=windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());view.setPadding(bottomLeft,bottomTop,bottomRight,bottomBottom+bars.bottom);return windowInsets;});
        ViewCompat.requestApplyInsets(header); ViewCompat.requestApplyInsets(bottom);
    }
}

