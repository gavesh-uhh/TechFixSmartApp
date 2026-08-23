package com.techfix.app.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import com.techfix.app.R;
import com.techfix.app.models.SampleRepair;

public class SampleImageAdapter extends RecyclerView.Adapter<SampleImageAdapter.Holder> {

    public interface OnSampleLongClickListener {
        void onSampleLongClick(SampleRepair sample);
    }

    private final List<SampleRepair> items = new ArrayList<>();
    private final OnSampleLongClickListener longClickListener;

    public SampleImageAdapter() { this(null); }

    public SampleImageAdapter(OnSampleLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void submit(List<SampleRepair> next) { items.clear(); items.addAll(next); notifyDataSetChanged(); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sample, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SampleRepair item = items.get(position);
        holder.title.setText(item.title);
        holder.service.setText(item.service);
        loadBitmap(holder.image, item.imageUri);

        if (longClickListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onSampleLongClick(item);
                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }
        holder.itemView.setLongClickable(longClickListener != null);
    }

    private void loadBitmap(ImageView target, String uri) {
        if (uri == null || uri.isEmpty()) {
            target.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = target.getContext().getContentResolver().openInputStream(Uri.parse(uri))) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = calculateInSampleSize(bounds, 720, 720);
            try (java.io.InputStream in = target.getContext().getContentResolver().openInputStream(Uri.parse(uri))) {
                Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
                if (bmp != null) {
                    target.setImageBitmap(bmp);
                } else {
                    target.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            }
        } catch (Exception e) {
            target.setImageResource(android.R.drawable.ic_menu_report_image);
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            while (height / (inSampleSize * 2) >= reqHeight && width / (inSampleSize * 2) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, service;
        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.sampleImage);
            title = itemView.findViewById(R.id.sampleTitle);
            service = itemView.findViewById(R.id.sampleService);
        }
    }
}
