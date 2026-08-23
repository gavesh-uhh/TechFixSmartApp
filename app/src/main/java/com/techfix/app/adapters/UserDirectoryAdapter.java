package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.techfix.app.R;
import com.techfix.app.models.User;
import java.util.ArrayList;
import java.util.List;

public class UserDirectoryAdapter extends RecyclerView.Adapter<UserDirectoryAdapter.Holder> {

    public interface UserRepairCountProvider {
        int getCount(long userId);
    }

    public interface OnUserClickListener {
        void onClick(User user);
    }

    private final List<User> items = new ArrayList<>();
    private final UserRepairCountProvider countProvider;
    private final OnUserClickListener clickListener;

    public UserDirectoryAdapter(UserRepairCountProvider countProvider, OnUserClickListener clickListener) {
        this.countProvider = countProvider;
        this.clickListener = clickListener;
    }

    public void submit(List<User> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        User user = items.get(position);

        holder.userName.setText(user.name != null && !user.name.isEmpty() ? user.name : "Customer");
        holder.userEmail.setText(user.email);
        holder.userPhone.setText(user.phone != null && !user.phone.isEmpty() ? "📞 " + user.phone : "📞 No phone registered");

        String initials = "TF";
        if (user.name != null && !user.name.trim().isEmpty()) {
            String[] parts = user.name.trim().split("\\s+");
            if (parts.length >= 2) {
                initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
            } else {
                initials = ("" + parts[0].charAt(0)).toUpperCase();
            }
        }
        holder.userAvatar.setText(initials);

        int count = (countProvider != null) ? countProvider.getCount(user.id) : 0;
        holder.userRepairCount.setText(count + (count == 1 ? " REPAIR" : " REPAIRS"));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(user);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView userAvatar, userName, userEmail, userPhone, userRepairCount;

        Holder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            userEmail = itemView.findViewById(R.id.userEmail);
            userPhone = itemView.findViewById(R.id.userPhone);
            userRepairCount = itemView.findViewById(R.id.userRepairCount);
        }
    }
}
