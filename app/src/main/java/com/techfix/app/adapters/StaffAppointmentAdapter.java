package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.AppointmentStatus;
import com.techfix.app.models.PaymentStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * StaffAppointmentAdapter - Displays repair appointments for staff with interactive workflow actions.
 */
public class StaffAppointmentAdapter extends RecyclerView.Adapter<StaffAppointmentAdapter.Holder> {

    public interface OnAppointmentClick { void onClick(Appointment appointment); }

    private final List<Appointment> items = new ArrayList<>();
    private final OnAppointmentClick listener;

    public StaffAppointmentAdapter(OnAppointmentClick listener) {
        this.listener = listener;
    }

    public void submit(List<Appointment> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_docket, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Appointment a = items.get(position);

        holder.number.setText(String.format("Docket #%05d", a.id));
        holder.stamp.setText(a.status);
        holder.title.setText(a.service);
        holder.subtitle.setText("Device: " + a.device + "\nIssue: " + a.problem);

        String paymentBadge = PaymentStatus.PAID.label.equalsIgnoreCase(a.payment) ? "PAID 🟢" : "PENDING ⏳";
        holder.meta.setText(a.branch + " · " + a.technician + " · Rs " + String.format("%,.0f", a.price) + " · " + paymentBadge);

        // Customize badge color based on status
        if (AppointmentStatus.COMPLETED.label.equalsIgnoreCase(a.status)) {
            holder.stamp.setTextColor(holder.itemView.getContext().getColor(R.color.success));
        } else if (AppointmentStatus.READY_FOR_COLLECTION.label.equalsIgnoreCase(a.status)) {
            holder.stamp.setTextColor(holder.itemView.getContext().getColor(R.color.techfix_blue_dark));
        } else if (AppointmentStatus.REPAIRING.label.equalsIgnoreCase(a.status) || AppointmentStatus.DIAGNOSING.label.equalsIgnoreCase(a.status)) {
            holder.stamp.setTextColor(holder.itemView.getContext().getColor(R.color.warning));
        } else {
            holder.stamp.setTextColor(holder.itemView.getContext().getColor(R.color.navy_700));
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(a));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView number, stamp, title, subtitle, meta;
        Holder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.docketNumber);
            stamp = itemView.findViewById(R.id.docketStamp);
            title = itemView.findViewById(R.id.docketTitle);
            subtitle = itemView.findViewById(R.id.docketSubtitle);
            meta = itemView.findViewById(R.id.docketMeta);
        }
    }
}
