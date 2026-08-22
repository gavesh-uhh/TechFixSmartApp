package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for managing repair service catalog & pricing in the Admin Dashboard.
 */
public class ServiceCatalogAdapter extends RecyclerView.Adapter<ServiceCatalogAdapter.Holder> {

    public interface OnServiceActionListener {
        void onEditPrice(Service service);
        void onDelete(Service service);
    }

    private final List<Service> items = new ArrayList<>();
    private final OnServiceActionListener listener;

    public ServiceCatalogAdapter(OnServiceActionListener listener) {
        this.listener = listener;
    }

    public void submit(List<Service> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_service, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Service service = items.get(position);

        holder.serviceName.setText(service.name);
        holder.serviceCategoryBadge.setText(service.category);
        holder.servicePrice.setText("Rs " + String.format("%,.0f", service.price));

        if (service.requiredPart != null && !service.requiredPart.trim().isEmpty()) {
            holder.serviceRequiredPart.setVisibility(View.VISIBLE);
            holder.serviceRequiredPart.setText("• Requires: " + service.requiredPart);
        } else {
            holder.serviceRequiredPart.setVisibility(View.GONE);
        }

        holder.btnEditPrice.setOnClickListener(v -> {
            if (listener != null) listener.onEditPrice(service);
        });

        holder.btnDeleteService.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(service);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView serviceName, serviceCategoryBadge, serviceRequiredPart, servicePrice;
        final Button btnEditPrice, btnDeleteService;

        Holder(@NonNull View itemView) {
            super(itemView);
            serviceName = itemView.findViewById(R.id.serviceName);
            serviceCategoryBadge = itemView.findViewById(R.id.serviceCategoryBadge);
            serviceRequiredPart = itemView.findViewById(R.id.serviceRequiredPart);
            servicePrice = itemView.findViewById(R.id.servicePrice);
            btnEditPrice = itemView.findViewById(R.id.btnEditPrice);
            btnDeleteService = itemView.findViewById(R.id.btnDeleteService);
        }
    }
}
