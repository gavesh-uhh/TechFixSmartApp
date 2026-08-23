package com.techfix.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Barebones Firebase Storage helper for repair damage photos. */
public final class RepairPhotoStorage {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private RepairPhotoStorage() {}

    /** Uploads the locally captured photo and swaps the stored local uri for the remote download url. */
    public static void upload(Context context, long appointmentId, Uri local) {
        IO.execute(() -> {
            try {
                StorageReference ref = FirebaseStorage.getInstance()
                        .getReference("appointment_photos/" + appointmentId + ".jpg");
                Tasks.await(ref.putFile(local));
                Uri remote = Tasks.await(ref.getDownloadUrl());
                new AppointmentDAO(DatabaseHelper.getInstance(context)).setPhoto(appointmentId, remote.toString());
            } catch (Exception ignored) {}
        });
    }

    /** Shows a photo from either a remote https url or a local file uri. */
    public static void load(ImageView view, String source) {
        if (source == null || source.isEmpty()) return;
        if (!source.startsWith("http")) { view.setImageURI(Uri.parse(source)); return; }
        IO.execute(() -> {
            Bitmap bmp = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(source).openConnection();
                c.setDoInput(true);
                c.connect();
                InputStream in = c.getInputStream();
                bmp = BitmapFactory.decodeStream(in);
                in.close();
            } catch (Exception ignored) {}
            Bitmap result = bmp;
            new Handler(Looper.getMainLooper()).post(() -> view.setImageBitmap(result));
        });
    }
}