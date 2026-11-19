package com.tapak.tracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {

    private final List<ActivityModel> list;
    private final OnActivityActionListener listener;

    public interface OnActivityActionListener {
        void onKudosClick(int position);
        void onDeleteClick(int position);
    }

    public ActivityAdapter(List<ActivityModel> list, OnActivityActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityModel item = list.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvDate.setText(item.date);
        holder.tvBadge.setText("Kategori: " + item.sportType + " • " + item.calories + " kkal");
        holder.tvDistance.setText(String.format(Locale.US, "%.2f km", item.distanceKm));
        holder.tvPace.setText(item.paceStr);
        holder.tvDuration.setText(item.durationStr);
        holder.btnKudos.setText("👍 Kudos (" + item.kudosCount + ")");

        holder.btnKudos.setOnClickListener(v -> listener.onKudosClick(holder.getAdapterPosition()));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvBadge, tvDistance, tvPace, tvDuration;
        Button btnKudos, btnDelete;

        ViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvItemTitle);
            tvDate = v.findViewById(R.id.tvItemDate);
            tvBadge = v.findViewById(R.id.tvItemSportBadge);
            tvDistance = v.findViewById(R.id.tvItemDistance);
            tvPace = v.findViewById(R.id.tvItemPace);
            tvDuration = v.findViewById(R.id.tvItemDuration);
            btnKudos = v.findViewById(R.id.btnKudos);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}
