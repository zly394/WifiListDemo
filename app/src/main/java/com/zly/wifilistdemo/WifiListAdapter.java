package com.zly.wifilistdemo;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class WifiListAdapter extends RecyclerView.Adapter<WifiListAdapter.WifiViewHolder> {
    private List<AccessPoint> list;
    private OnItemClickListener itemClickListener;

    public interface OnItemClickListener {
        void onClick(AccessPoint accessPoint);
    }

    public void setAccessPoints(List<AccessPoint> list) {
        this.list = list;
        notifyDataSetChanged();
    }

    public void setItemClickListener(OnItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public WifiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new WifiViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wifi, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WifiViewHolder holder, int position) {
        AccessPoint accessPoint = list.get(position);
        holder.tvName.setText(accessPoint.ssid);
        if (accessPoint.isSaved()) {
            holder.tvState.setVisibility(View.VISIBLE);
            holder.tvState.setText(accessPoint.getStatusSummary());
        } else {
            holder.tvState.setVisibility(View.GONE);
        }
        if (accessPoint.isSecured) {
            holder.ivSignal.setImageResource(R.drawable.icon_wifi_signal_lock_level);
        } else {
            holder.ivSignal.setImageResource(R.drawable.icon_wifi_signal_level);
        }
        holder.ivSignal.setImageLevel(accessPoint.getSignalLevel());
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onClick(accessPoint);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class WifiViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvState;
        ImageView ivSignal;

        public WifiViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvState = itemView.findViewById(R.id.tvState);
            ivSignal = itemView.findViewById(R.id.ivSignal);
        }
    }
}
