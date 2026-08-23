package com.techfix.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techfix.app.R;
import com.techfix.app.models.BannerItem;

import java.util.ArrayList;
import java.util.List;

public class BannerCarouselAdapter extends RecyclerView.Adapter<BannerCarouselAdapter.BannerViewHolder> {

    private final List<BannerItem> items = new ArrayList<>();

    public BannerCarouselAdapter(List<BannerItem> bannerList) {
        if (bannerList != null) {
            this.items.addAll(bannerList);
        }
    }

    public void submitList(List<BannerItem> bannerList) {
        this.items.clear();
        if (bannerList != null) {
            this.items.addAll(bannerList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner_carousel_card, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        BannerItem item = items.get(position);
        holder.bannerTag.setText(item.getTag());
        holder.bannerTitle.setText(item.getTitle());
        holder.bannerSubtitle.setText(item.getSubtitle());

        if (holder.bannerImageView != null && item.getBackgroundRes() != 0) {
            holder.bannerImageView.setImageResource(item.getBackgroundRes());
        }

        if (item.getTagTextColorHex() != 0) {
            holder.bannerTag.setTextColor(item.getTagTextColorHex());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        final ImageView bannerImageView;
        final LinearLayout bannerBackground;
        final TextView bannerTag;
        final TextView bannerTitle;
        final TextView bannerSubtitle;

        BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            bannerImageView = itemView.findViewById(R.id.bannerImageView);
            bannerBackground = itemView.findViewById(R.id.bannerCardBackground);
            bannerTag = itemView.findViewById(R.id.bannerTag);
            bannerTitle = itemView.findViewById(R.id.bannerTitle);
            bannerSubtitle = itemView.findViewById(R.id.bannerSubtitle);
        }
    }
}
