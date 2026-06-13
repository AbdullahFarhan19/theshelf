package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class OutfitImageAdapter extends RecyclerView.Adapter<OutfitImageAdapter.ViewHolder> {

    private final List<WardrobeItem> items;

    public OutfitImageAdapter(List<WardrobeItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_outfit_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WardrobeItem item = items.get(position);
        holder.tvItemName.setText(item.getName());

        String path = item.getImagePath();
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(path);
                if (bmp != null) {
                    holder.ivItem.setImageBitmap(bmp);
                    holder.ivItem.setVisibility(View.VISIBLE);
                    return;
                }
            }
        }
        holder.ivItem.setVisibility(View.GONE); // No photo — collapse the image slot
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivItem;
        TextView  tvItemName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivItem     = itemView.findViewById(R.id.iv_outfit_item);
            tvItemName = itemView.findViewById(R.id.tv_outfit_item_name);
        }
    }
}