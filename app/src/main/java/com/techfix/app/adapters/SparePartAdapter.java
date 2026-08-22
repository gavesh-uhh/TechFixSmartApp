package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.SparePart;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for managing spare parts inventory in the Admin Dashboard.
 */
public class SparePartAdapter extends RecyclerView.Adapter<SparePartAdapter.Holder> {

    public interface OnPartActionListener {
        void onAdjustQuantity(SparePart part, int delta);
        void onDelete(SparePart part);
    }

    private final List<SparePart> items = new ArrayList<>();
    private final OnPartActionListener listener;

    public SparePartAdapter(OnPartActionListener listener) {
        this.listener = listener;
    }

    public void submit(List<SparePart> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_part, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SparePart part = items.get(position);

        holder.partName.setText(part.name);
        holder.partBranch.setText(part.branch);

        // Dynamic stock status badge
        if (part.quantity == 0) {
            holder.partStockBadge.setText("OUT OF STOCK");
            holder.partStockBadge.setTextColor(holder.itemView.getContext().getColor(R.color.error));
        } else if (part.quantity < 3) {
            holder.partStockBadge.setText("LOW (" + part.quantity + " LEFT)");
            holder.partStockBadge.setTextColor(holder.itemView.getContext().getColor(R.color.warning));
        } else {
            holder.partStockBadge.setText(part.quantity + " IN STOCK");
            holder.partStockBadge.setTextColor(holder.itemView.getContext().getColor(R.color.success));
        }

        holder.btnMinusOne.setEnabled(part.quantity > 0);
        holder.btnMinusOne.setOnClickListener(v -> {
            if (listener != null) listener.onAdjustQuantity(part, -1);
        });

        holder.btnPlusOne.setOnClickListener(v -> {
            if (listener != null) listener.onAdjustQuantity(part, 1);
        });

        holder.btnPlusFive.setOnClickListener(v -> {
            if (listener != null) listener.onAdjustQuantity(part, 5);
        });

        holder.btnDeletePart.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(part);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView partName, partBranch, partStockBadge;
        final Button btnMinusOne, btnPlusOne, btnPlusFive, btnDeletePart;

        Holder(@NonNull View itemView) {
            super(itemView);
            partName = itemView.findViewById(R.id.partName);
            partBranch = itemView.findViewById(R.id.partBranch);
            partStockBadge = itemView.findViewById(R.id.partStockBadge);
            btnMinusOne = itemView.findViewById(R.id.btnMinusOne);
            btnPlusOne = itemView.findViewById(R.id.btnPlusOne);
            btnPlusFive = itemView.findViewById(R.id.btnPlusFive);
            btnDeletePart = itemView.findViewById(R.id.btnDeletePart);
        }
    }
}
