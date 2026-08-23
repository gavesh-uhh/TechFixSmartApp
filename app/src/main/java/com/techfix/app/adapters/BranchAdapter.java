package com.techfix.app.adapters;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.Branch;
import java.util.ArrayList;
import java.util.List;

public class BranchAdapter extends RecyclerView.Adapter<BranchAdapter.Holder> {

    private final List<Branch> items = new ArrayList<>();

    public void submit(List<Branch> next) { items.clear(); items.addAll(next); notifyDataSetChanged(); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_branch, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Branch b = items.get(position);
        holder.name.setText(b.name);
        holder.city.setText(b.city);
        holder.coords.setText(b.latitude + ", " + b.longitude);
        holder.mapButton.setOnClickListener(v -> v.getContext().startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:" + b.latitude + "," + b.longitude + "?q=" + Uri.encode(b.name)))));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, city, coords;
        final Button mapButton;
        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.branchName);
            city = itemView.findViewById(R.id.branchCity);
            coords = itemView.findViewById(R.id.branchCoords);
            mapButton = itemView.findViewById(R.id.branchMapButton);
        }
    }
}
