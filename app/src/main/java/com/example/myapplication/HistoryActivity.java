package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Displays Every Wardrobe Item The User Has Ever Added In A Vertical Scrollable List
// Each Row Shows The Photo Thumbnail, Item Name, Category, Weather Tag, And Date Added
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerHistory;      // Vertical List That Shows All Items From The DB
    private WardrobeDatabase db;               // Reference To The Database Singleton
    private List<WardrobeItem> historyItems;   // Full List Loaded From The DB
    private HistoryAdapter historyAdapter;     // Adapter That Binds Each WardrobeItem To A Row View

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = WardrobeDatabase.getInstance(this); // Get The Single Shared Database Connection

        // Back Arrow In The Top Left — Closes This Activity And Returns To MainScreen
        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish()); // finish() Removes This Activity From The Stack And Returns To The Previous One

        recyclerHistory = findViewById(R.id.recycler_history);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this)); // Vertical Linear List Instead Of A Grid

        historyItems = new ArrayList<>(db.getAllItems()); // Load All Saved Items, Newest First
        historyAdapter = new HistoryAdapter(historyItems);
        recyclerHistory.setAdapter(historyAdapter);

        // Show A Friendly Empty State If The Wardrobe Has No Items Yet
        TextView tvNoHistory = findViewById(R.id.tv_no_history);
        if (historyItems.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            recyclerHistory.setVisibility(View.GONE);
        } else {
            tvNoHistory.setVisibility(View.GONE);
            recyclerHistory.setVisibility(View.VISIBLE);
        }
    }

    // Inner Adapter Class — Kept Here Because It Is Only Ever Used By This Activity
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

        private final List<WardrobeItem> items; // The List Of Items This Adapter Renders
        private final SimpleDateFormat dateFormatter; // Reused For Every Row To Avoid Creating It Repeatedly

        HistoryAdapter(List<WardrobeItem> items) {
            this.items = items;
            // Format The Timestamp Into A Human-Readable String Like "April 25, 2026"
            this.dateFormatter = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        }

        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(HistoryActivity.this)
                    .inflate(R.layout.item_history_row, parent, false); // Inflate The Row Layout
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            WardrobeItem item = items.get(position);

            holder.tvName.setText(item.getName());
            holder.tvCategory.setText(item.getCategory());
            holder.tvWeather.setText(item.getWeather());
            // Convert The Stored Unix Timestamp Into A Date String For Display
            holder.tvDate.setText(dateFormatter.format(new Date(item.getTimestamp())));

            // Load The Saved Photo If One Exists For This Item
            if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
                Bitmap bmp = BitmapFactory.decodeFile(item.getImagePath());
                if (bmp != null) {
                    holder.ivThumbnail.setImageBitmap(bmp);
                } else {
                    holder.ivThumbnail.setImageResource(R.drawable.ic_photo_placeholder); // Fall Back To Placeholder Icon
                }
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.ic_photo_placeholder); // No Photo Was Taken For This Item
            }

            // Long Press On A Row Offers The Option To Delete That Item Permanently
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("Remove Item")
                        .setMessage("Remove \"" + item.getName() + "\" from your wardrobe?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            ImageStorageHelper.deleteImageFromInternalStorage(item.getImagePath()); // Clean Up The Photo File First
                            db.deleteItem(item.getId()); // Then Remove The Database Row
                            int idx = holder.getAdapterPosition();
                            items.remove(idx); // Remove From The In-Memory List
                            notifyItemRemoved(idx); // Animate The Row Out Smoothly
                            Toast.makeText(HistoryActivity.this, "Item removed.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true; // Consume The Event
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // Caches The Child View References To Avoid Repeated findViewById Calls During Scrolling
        class HistoryViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumbnail;  // Square Photo Thumbnail On The Left Side Of Each Row
            TextView tvName;        // Bold Item Name
            TextView tvCategory;    // Category Badge
            TextView tvWeather;     // Weather Tag
            TextView tvDate;        // Human-Readable Date When The Item Was Added

            HistoryViewHolder(View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.history_thumbnail);
                tvName      = itemView.findViewById(R.id.history_name);
                tvCategory  = itemView.findViewById(R.id.history_category);
                tvWeather   = itemView.findViewById(R.id.history_weather);
                tvDate      = itemView.findViewById(R.id.history_date);
            }
        }
    }
}
