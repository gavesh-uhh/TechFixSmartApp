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

/** Gallery adapter for sample repaired-device images. */
public class SampleImageAdapter extends RecyclerView.Adapter<SampleImageAdapter.Holder> {

    private final List<SampleRepair> items = new ArrayList<>();

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
        if (item.imageUri.isEmpty()) {
            holder.image.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            try (java.io.InputStream in = holder.image.getContext().getContentResolver().openInputStream(Uri.parse(item.imageUri))) {
                Bitmap bmp = BitmapFactory.decodeStream(in);
                holder.image.setImageBitmap(bmp);
            } catch (Exception e) {
                holder.image.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        }
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

