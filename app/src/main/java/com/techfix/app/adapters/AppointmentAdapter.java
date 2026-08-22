package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.Appointment;

/** RecyclerView adapter for the customer's repair list (Complex Data Model &amp; Adaptors deliverable). */
public class AppointmentAdapter extends ListAdapter<Appointment, AppointmentAdapter.Holder> {

    public interface OnAppointmentClick { void onClick(Appointment appointment); }

    private static final DiffUtil.ItemCallback<Appointment> DIFF = new DiffUtil.ItemCallback<Appointment>() {
        @Override public boolean areItemsTheSame(@NonNull Appointment a, @NonNull Appointment b) { return a.id == b.id; }
        @Override public boolean areContentsTheSame(@NonNull Appointment a, @NonNull Appointment b) {
            return a.status.equals(b.status) && a.payment.equals(b.payment) && a.technician.equals(b.technician);
        }
    };

    private final OnAppointmentClick listener;

    public AppointmentAdapter(OnAppointmentClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_docket, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final Appointment item = getItem(position);
        holder.number.setText(String.format("#%06d", item.id));
        holder.stamp.setText(item.status);
        holder.title.setText(item.device + " · " + item.service);
        holder.subtitle.setText(item.problem);
        holder.meta.setText(item.branch + " · Rs " + String.format("%,.0f", item.price) + " · " + item.payment
                + (item.createdAt.isEmpty() ? "" : "\n" + item.createdAt));
        holder.itemView.setOnClickListener(v -> listener.onClick(item));
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
