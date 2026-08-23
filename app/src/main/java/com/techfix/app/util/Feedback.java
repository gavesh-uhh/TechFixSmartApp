package com.techfix.app.util;

import android.view.View;
import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;

public final class Feedback {
    private Feedback() { }
    public static void success(View view,String message){show(view,message,R.color.success);}
    public static void error(View view,String message){show(view,message,R.color.error);}
    private static void show(View view,String message,int color){Snackbar bar=Snackbar.make(view,message,Snackbar.LENGTH_LONG);bar.setBackgroundTint(view.getContext().getColor(color));bar.setTextColor(view.getContext().getColor(R.color.white));bar.show();}
}
