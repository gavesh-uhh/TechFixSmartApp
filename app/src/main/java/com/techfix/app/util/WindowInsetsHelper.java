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
        ViewCompat.setOnApplyWindowInsetsListener(header,(view,windowInsets)->{
            Insets bars=windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets cutout=windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            view.setPadding(Math.max(headerLeft,cutout.left),headerTop+Math.max(bars.top,cutout.top),Math.max(headerRight,cutout.right),headerBottom);return windowInsets;});
        ViewCompat.setOnApplyWindowInsetsListener(bottom,(view,windowInsets)->{
            Insets bars=windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime=windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bottomLeft,bottomTop,bottomRight,bottomBottom+bars.bottom+ime.bottom);return windowInsets;});
        ViewCompat.requestApplyInsets(header); ViewCompat.requestApplyInsets(bottom);
    }

    /**
     * Pads only the top by max(statusBars, displayCutout) inset, preserving existing padding.
     * Use for headers when the bottom view handles its own insets (e.g. Material BottomNavigationView).
     */
    public static void applyHeader(View header) {
        int left=header.getPaddingLeft(), top=header.getPaddingTop(), right=header.getPaddingRight(), bottom=header.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(header,(view,windowInsets)->{
            Insets bars=windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets cutout=windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            view.setPadding(Math.max(left,cutout.left),top+Math.max(bars.top,cutout.top),Math.max(right,cutout.right),bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(header);
    }

    /**
     * Pads only the bottom by navigationBars + ime() insets so footers stay above the
     * gesture nav bar and the keyboard. Preserves existing padding.
     */
    public static void applyBottomInset(View footer) {
        int left=footer.getPaddingLeft(), top=footer.getPaddingTop(), right=footer.getPaddingRight(), bottom=footer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(footer,(view,windowInsets)->{
            Insets nav=windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime=windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(left,top,right,bottom+nav.bottom+ime.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(footer);
    }

    /**
     * For scrollable content screens: pads top by max(statusBars, displayCutout) and bottom by
     * navigationBars + ime(). Single listener so both edges work on the same view.
     */
    public static void applyScrollContent(View content) {
        int left=content.getPaddingLeft(), top=content.getPaddingTop(), right=content.getPaddingRight(), bottom=content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content,(view,windowInsets)->{
            Insets bars=windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets cutout=windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets nav=windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime=windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(left,top+Math.max(bars.top,cutout.top),right,bottom+nav.bottom+ime.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}

