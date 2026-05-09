package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// RecyclerView Is Used To Display Large Scrollable Data Sets
// A New View For Every Item In The List — This Makes Scrolling Smooth Even With Many Items And Prevents A Laggy And Choppy User Experience
public class WardrobeAdapter extends RecyclerView.Adapter<WardrobeAdapter.ItemViewHolder> {

    private final Context context;         // Needed For Inflating Views And Loading Images
    private List<WardrobeItem> itemList;   // The Current Data Set Being Displayed

    // Callback Interface So That The Activity Handles The Event
    public interface OnItemLongClickListener {
        void onItemLongClick(WardrobeItem item, int position);
    }

    private OnItemLongClickListener longClickListener; // Stored Reference To The Listener Set By The Activity

    public WardrobeAdapter(Context context, List<WardrobeItem> itemList) {
        this.context = context;
        this.itemList = itemList;
    }

    // Allows The Activity To Register Itself As A Long-Click Handler
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    // Called By RecyclerView When It Needs A New View — Once Enough Views Have Been Generated, It Starts Reusing Them
    @NonNull // Cannot Return Null
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { // ViewGroup Cannot Hold A Null Value
        View view = LayoutInflater.from(context).inflate(R.layout.item_wardrobe_card, parent, false); // Do Not Attach To Parent As Of Yet
        return new ItemViewHolder(view);
    }

    // Called By RecyclerView To Bind Data To A ViewHolder That Has Been Recycled Or Newly Created
    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        WardrobeItem item = itemList.get(position);

        holder.tvName.setText(item.getName());         // Display The Item's Name
        holder.tvCategory.setText(item.getCategory()); // Display Its Category Badge

        // Try To Load The Saved Image From Disk If A Path Was Recorded
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            Bitmap bmp = BitmapFactory.decodeFile(item.getImagePath()); // Reads The File And Decodes It Into A Bitmap
            if (bmp != null) {
                holder.ivPhoto.setImageBitmap(bmp);
                holder.ivPhoto.setVisibility(View.VISIBLE);
                holder.tvNoPhoto.setVisibility(View.GONE); // Hide The Placeholder Text Since We Have A Real Photo
            } else {
                // File Exists But Couldn't Be Decoded — Show Placeholder
                holder.ivPhoto.setVisibility(View.GONE);
                holder.tvNoPhoto.setVisibility(View.VISIBLE);
            }
        } else {
            // No Path Was Saved — Item Was Added Without A Photo
            holder.ivPhoto.setVisibility(View.GONE);
            holder.tvNoPhoto.setVisibility(View.VISIBLE);
        }

        // Wire Up The Long Click So The Activity Can Offer A Delete Option
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(item, holder.getBindingAdapterPosition());
            }
            return true; // Returning True Consumes The Event So It Doesn't Bubble Up Further
        });
    }

    // Returns The Total Number Of Items So RecyclerView Knows How Many Cards To Render
    @Override
    public int getItemCount() {
        return itemList.size();
    }

    // Replaces The Entire Dataset And Tells RecyclerView To Redraw All Items
    public void updateData(List<WardrobeItem> newItems) {
        this.itemList = newItems;
        notifyDataSetChanged(); // Simple Refresh
    }

    // ViewHolder Caches References To The Child Views So We Don't Call findViewById Repeatedly
    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;    // Shows The Item's Photo If One Was Taken
        TextView tvName;      // The Name Label Below The Photo
        TextView tvCategory;  // A Small Category Badge In The Corner
        TextView tvNoPhoto;   // Shown When No Photo Is Available As A Friendly Placeholder

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto    = itemView.findViewById(R.id.item_photo);
            tvName     = itemView.findViewById(R.id.item_name);
            tvCategory = itemView.findViewById(R.id.item_category);
            tvNoPhoto  = itemView.findViewById(R.id.item_no_photo);
        }
    }
}
