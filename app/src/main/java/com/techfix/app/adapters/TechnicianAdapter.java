package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.Technician;
import java.util.ArrayList;
import java.util.List;

public class TechnicianAdapter extends RecyclerView.Adapter<TechnicianAdapter.Holder> {

    public interface OnTechnicianActionListener {
        void onToggleDuty(Technician technician);
        void onDelete(Technician technician);
    }

    public interface ActiveJobCountProvider {
        int getCount(String techName);
    }

    private final List<Technician> items = new ArrayList<>();
    private final OnTechnicianActionListener listener;
    private final ActiveJobCountProvider countProvider;

    public TechnicianAdapter(OnTechnicianActionListener listener, ActiveJobCountProvider countProvider) {
        this.listener = listener;
        this.countProvider = countProvider;
    }

    public void submit(List<Technician> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_technician, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Technician tech = items.get(position);

        holder.techName.setText(tech.name);
        holder.techDetails.setText(tech.branch + " · " + tech.skill);

        if (tech.available) {
            holder.techStatusBadge.setText("ON DUTY");
            holder.techStatusBadge.setTextColor(holder.itemView.getContext().getColor(R.color.success));
            holder.btnToggleDuty.setText("Set Off-Duty / Busy");
        } else {
            holder.techStatusBadge.setText("BUSY / OFF-DUTY");
            holder.techStatusBadge.setTextColor(holder.itemView.getContext().getColor(R.color.error));
            holder.btnToggleDuty.setText("Set Available");
        }

        int activeJobs = (countProvider != null) ? countProvider.getCount(tech.name) : 0;
        holder.techActiveJobs.setText(activeJobs + (activeJobs == 1 ? " Active Job" : " Active Jobs"));

        holder.btnToggleDuty.setOnClickListener(v -> {
            if (listener != null) listener.onToggleDuty(tech);
        });

        holder.btnDeleteTech.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(tech);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView techName, techDetails, techStatusBadge, techActiveJobs;
        final Button btnToggleDuty, btnDeleteTech;

        Holder(@NonNull View itemView) {
            super(itemView);
            techName = itemView.findViewById(R.id.techName);
            techDetails = itemView.findViewById(R.id.techDetails);
            techStatusBadge = itemView.findViewById(R.id.techStatusBadge);
            techActiveJobs = itemView.findViewById(R.id.techActiveJobs);
            btnToggleDuty = itemView.findViewById(R.id.btnToggleDuty);
            btnDeleteTech = itemView.findViewById(R.id.btnDeleteTech);
        }
    }
}
