package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainScreen extends AppCompatActivity {

    ActivityResultLauncher<Void> cameraLauncher;   // Launches An Activity And Can Be Used To Get Output From It, It Takes No Input
    ActivityResultLauncher<String> permissionLauncher; // Takes A String Input So That It Knows Which Permission It Needs To Ask

    private FrameLayout currentDialogPhotoZone;
    private LinearLayout currentDialogPlaceholderUi;
    private ImageView currentDialogImagePreview;
    private Spinner currentDialogSpinnerCategory; // Keeps Track Of The Spinner For ML Kit

    // Global References For Our Mutable Category List And Its Adapter
    private ArrayList<String> categoryItems; // Array List Of Category Items, Allows Category Options To Be Mutated As Opposed To A Static XML List
    private ArrayAdapter<String> categoryAdapter;

    // Holds The Bitmap From The Camera Until The User Taps Save So We Can Persist It Then
    private Bitmap capturedBitmap;
    private String capturedColor; // Holds The Detected Color Until The User Taps Save

    // RecyclerView And Its Associated Adapter And Data List For The Wardrobe Grid
    private RecyclerView recyclerView;
    private WardrobeAdapter wardrobeAdapter;
    private List<WardrobeItem> wardrobeItems;

    // Reference To The Database Helper Singleton
    private WardrobeDatabase db;

    // Reference To The Empty State Hint Text Shown When The Wardrobe Has No Items Yet
    private TextView tvEmptyHint;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        cameraLauncher = registerForActivityResult( // Tells Android To Expect An Activity/App Launch In The Near Future
                new ActivityResultContracts.TakePicturePreview(), // Open The Default Camera, Let The User Snap A Photo And Then Come Back With A Small Preview
                new ActivityResultCallback<Bitmap>() { // This Wakes Up When The Camera App Is Closed/The Picture Is Taken
                    @Override
                    public void onActivityResult(Bitmap result) { // When The Pic Is Delivered
                        if (result != null) {
                            Toast.makeText(MainScreen.this, "Item Captured Successfully!", Toast.LENGTH_SHORT).show(); // LENGTH_SHORT Makes The Toast Linger For A Small Amount Of Time, LENGTH_LONG Would Make It Linger For Longer

                            capturedBitmap = result; // Store The Bitmap So We Can Save It When The User Taps Save

                            if (currentDialogPlaceholderUi != null && currentDialogImagePreview != null) {
                                currentDialogPlaceholderUi.setVisibility(View.GONE);
                                currentDialogImagePreview.setVisibility(View.VISIBLE);
                                currentDialogImagePreview.setImageBitmap(result); // Shows The Image Taken Inside The Dialog Preview
                            }

                            if (currentDialogSpinnerCategory != null) {
                                analyzeImageWithMLKit(result, currentDialogSpinnerCategory);
                            }

                        } else {
                            Toast.makeText(MainScreen.this, "Failed To Capture The Picture", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), // Displays The Android Popup Asking For Permission
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (granted) {
                            cameraLauncher.launch(null);
                        } else {
                            Toast.makeText(MainScreen.this, "The app needs camera permissions to take photos!", Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        // Grab The Database Singleton So We Can Read And Write Items Throughout This Activity
        db = WardrobeDatabase.getInstance(this);

        // Wire Up The Empty State Hint Text That Lives In The Center Of The Screen
        tvEmptyHint = findViewById(R.id.tv_empty_hint);

        // Set Up The RecyclerView As A Two-Column Grid To Display All Saved Wardrobe Items
        recyclerView = findViewById(R.id.recycler_wardrobe);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2)); // 2 Columns Side By Side

        wardrobeItems = new ArrayList<>(db.getAllItems()); // Load Any Previously Saved Items From The DB On Launch
        wardrobeAdapter = new WardrobeAdapter(this, wardrobeItems);
        recyclerView.setAdapter(wardrobeAdapter); // Attach The Adapter So RecyclerView Knows What To Draw

        // Show Or Hide The Empty State Hint Depending On Whether The Wardrobe Has Items
        updateEmptyState();

        // Long-Press A Card To Get The Option To Delete That Item From The Wardrobe
        wardrobeAdapter.setOnItemLongClickListener((item, position) -> showDeleteConfirmation(item, position));

        ImageView btnHistory = findViewById(R.id.btn_history);
        CardView btnAdd = findViewById(R.id.btn_add); // CardViews Are A Way To Group Views
        ImageView btnSuggestions = findViewById(R.id.btn_suggestions);

        // History Button — Opens The Full Wardrobe History Screen
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainScreen.this, HistoryActivity.class); // Navigate To The History Screen
                startActivity(i);
            }
        });

        // Add Item Button — Launches The Add Item Bottom Sheet Dialog
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturedBitmap = null; // Clear Any Leftover Bitmap From A Previous Dialog Session
                showAddItemDialog();
            }
        });

        // Suggestions Button — Opens The AI Outfit Suggestions Screen
        btnSuggestions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainScreen.this, SuggestionsActivity.class); // Navigate To The Suggestions Screen
                startActivity(i);
            }
        });
    }

    // Called Every Time This Activity Comes Back Into View (E.G. After Returning From History Or Suggestions)
    // This Ensures The Grid Always Reflects The Latest Database State
    @Override
    protected void onResume() {
        super.onResume();
        refreshWardrobeGrid(); // Pull Fresh Data From The DB And Redraw The Grid
    }

    // Rebuilds The Wardrobe Grid By Fetching All Items From The Database
    private void refreshWardrobeGrid() {
        wardrobeItems = new ArrayList<>(db.getAllItems());
        wardrobeAdapter.updateData(wardrobeItems); // Tell The Adapter The Data Changed So It Redraws
        updateEmptyState();
    }

    // Shows The "Tap '+' To Start Adding Items" Hint Only When The Wardrobe Is Empty
    private void updateEmptyState() {
        if (wardrobeItems.isEmpty()) {
            tvEmptyHint.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE); // Hide The Grid When There Is Nothing To Show
        } else {
            tvEmptyHint.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE); // Show The Grid When At Least One Item Exists
        }
    }

    // Shows A Confirmation Dialog Before Permanently Deleting An Item And Its Photo File
    private void showDeleteConfirmation(WardrobeItem item, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Item")
                .setMessage("Remove \"" + item.getName() + "\" from your wardrobe? This cannot be undone.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    // Delete The Image File From Internal Storage First To Reclaim Disk Space
                    ImageStorageHelper.deleteImageFromInternalStorage(item.getImagePath());
                    // Then Remove The Database Row So The Item No Longer Appears Anywhere In The App
                    db.deleteItem(item.getId());
                    refreshWardrobeGrid(); // Redraw The Grid Without The Deleted Item
                    Toast.makeText(this, "\"" + item.getName() + "\" removed.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null) // null Means The Dialog Just Closes With No Action
                .show();
    }

    private void showAddItemDialog() {
        LayoutInflater inflater = LayoutInflater.from(this); // Takes XML File And "Inflates" It Into A Java View Object, This Means The Current Screen
        View dialogView = inflater.inflate(R.layout.dialog_add_item, null); // This Executes The Inflation, Builds It In Memory, null Means No Parent Has Been Assigned, The Current Screen Is The Parent

        AlertDialog.Builder builder = new AlertDialog.Builder(this); // Builder Used To Build The Dialog Object
        builder.setView(dialogView); // Set Builder's View To Inflated Object
        AlertDialog dialog = builder.create(); // Finishes The Instruction

        currentDialogPhotoZone = dialogView.findViewById(R.id.dialog_photo_zone); // Selects The Photo Zone Inside The Dialog, dialogView.findViewById Used Instead Of findViewById Because Simply findViewById Would Cause It To Look In MainScreen And Not DialogView
        currentDialogPlaceholderUi = dialogView.findViewById(R.id.dialog_photo_placeholder_ui); // Contains The Image Icon And The Prompt Text
        currentDialogImagePreview = dialogView.findViewById(R.id.dialog_image_preview); // Image Preview After Image Captured

        EditText inputName = dialogView.findViewById(R.id.dialog_input_name);
        currentDialogSpinnerCategory = dialogView.findViewById(R.id.dialog_spinner_category);

        categoryItems = new ArrayList<>(Arrays.asList( // List Of Categories To Be Displayed In The Spinner
                "Select Category...",
                "Tops",
                "Bottoms",
                "Outerwear",
                "Shoes",
                "Wristwear",
                "Headwear",
                "Facewear",
                "Neckwear",
                "AI Suggestion: Pending..." // Index 9 (The Last Option)
        ));

        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryItems); // Maps The Data Source, An Array In This Case, To The UI Element
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); // A Stylistic Addition That Adds A Bit Of Padding Between The Options, Built In
        currentDialogSpinnerCategory.setAdapter(categoryAdapter); // Applies The Adapter To The Spinner

        Spinner spinnerWeather = dialogView.findViewById(R.id.dialog_spinner_weather);
        Button btnCancel = dialogView.findViewById(R.id.dialog_btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.dialog_btn_save);

        currentDialogPhotoZone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                permissionLauncher.launch(android.Manifest.permission.CAMERA); // Launches Permission Dialog Box To Launch Camera
            }
        });

        btnCancel.setOnClickListener(v -> {
            capturedBitmap = null; // Discard The Photo If The User Cancels The Dialog
            dialog.dismiss();
        });

        // Handle Save Button — Validates Input, Saves Photo To Disk, Then Writes To DB
        btnSave.setOnClickListener(v -> {
            String itemName = inputName.getText().toString().trim();
            String category = currentDialogSpinnerCategory.getSelectedItem().toString();
            String weather = spinnerWeather.getSelectedItem().toString();

            // Basic Validation: Name And A Real Category Selection Are The Minimum Requirements
            if (itemName.isEmpty() || category.equals("Select Category...")) {
                Toast.makeText(this, "Please provide a name and category", Toast.LENGTH_SHORT).show();
                return;
            }

            // If The User Took A Photo, Save It To Internal Storage And Record The File Path
            String savedImagePath = null;
            if (capturedBitmap != null) {
                String fileName = ImageStorageHelper.generateUniqueFileName(); // Unique Name Prevents Collisions
                savedImagePath = ImageStorageHelper.saveBitmapToInternalStorage(this, capturedBitmap, fileName);
            }

            // Build The Item Model And Persist It To The SQLite Database
            WardrobeItem newItem = new WardrobeItem(itemName, category, weather, capturedColor, savedImagePath);
            long insertedId = db.insertItem(newItem);

            if (insertedId != -1) { // -1 Means The Insert Failed
                Toast.makeText(this, "Saved " + itemName + " to The Shelf!", Toast.LENGTH_SHORT).show();
                capturedBitmap = null; // Clear The Bitmap Now That It Has Been Safely Written To Disk
                refreshWardrobeGrid(); // Redraw The Grid So The New Item Appears Immediately
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Failed to save item. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });

        // Show The Window
        dialog.show();
    }

    private void analyzeImageWithMLKit(Bitmap bitmap, Spinner spinnerCategory) {
        capturedColor = detectDominantColor(bitmap); // Extract The Dominant Color Before Sending To ML Kit

        InputImage image = InputImage.fromBitmap(bitmap, 0); // Converts bitmap Into A Usable Format For The AI

        ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS); // Wakes Up AI, Default Image Labeler With No Internet Required Is Used

        labeler.process(image).addOnSuccessListener(labels -> { // Asynchronous Callback, Waits For The AI To Finish While The Rest Of The App Keeps Running Smoothly
            for (ImageLabel label : labels) { // Iterates Through All Labels Generated
                String text = label.getText().toLowerCase();
                float confidence = label.getConfidence();

                if (confidence > 0.7f) {
                    String mappedCategory = mapAILabelToCategoryName(text); // Returns A String Of The Mapped Category

                    if (mappedCategory != null) {
                        // Finds The Exact Index Of The Last Option In Our List
                        int lastIndex = categoryItems.size() - 1;

                        // Overwrite The Text At That Last Position With The AI's Finding
                        categoryItems.set(lastIndex, "AI Detected: " + mappedCategory);

                        // Notify The Adapter That The Underlying Data Has Changed So It Refreshes The UI
                        categoryAdapter.notifyDataSetChanged();

                        // Automatically Switch The Spinner To Display This Newly Updated Last Option
                        spinnerCategory.setSelection(lastIndex);

                        Toast.makeText(MainScreen.this, "AI Has Identified The Image Provided! Check The Last Option Of The DropDown Menu", Toast.LENGTH_SHORT).show();

                        // Break Out Of The Loop So It Only Updates Based On The First Highly Confident Match
                        break;
                    }
                }
            }
        }).addOnFailureListener(e -> {
            e.printStackTrace(); // Shows The Stack Path To Thrown Exception
        });
    }

    // It Returns The Actual Text Name Of The Category That The AI Has Detected Or Predicted
    private String mapAILabelToCategoryName(String aiLabel) {
        if (aiLabel.contains("shirt") || aiLabel.contains("top") || aiLabel.contains("blouse")) {
            return "Tops";
        }
        if (aiLabel.contains("pants") || aiLabel.contains("jeans") || aiLabel.contains("shorts") || aiLabel.contains("trousers")) {
            return "Bottoms";
        }
        if (aiLabel.contains("jacket") || aiLabel.contains("coat") || aiLabel.contains("outerwear") || aiLabel.contains("hoodie")) {
            return "Outerwear";
        }
        if (aiLabel.contains("shoe") || aiLabel.contains("footwear") || aiLabel.contains("sneaker") || aiLabel.contains("boot")) {
            return "Shoes";
        }
        if (aiLabel.contains("wrist wear") || aiLabel.contains("ring") || aiLabel.contains("watch") || aiLabel.contains("bracelet")) {
            return "Wristwear";
        }
        if (aiLabel.contains("headwear") || aiLabel.contains("hat") || aiLabel.contains("cap") || aiLabel.contains("crown") || aiLabel.contains("headband")) {
            return "Headwear";
        }
        if (aiLabel.contains("glasses") || aiLabel.contains("facewear") || aiLabel.contains("earrings") || aiLabel.contains("nose ring")) {
            return "Facewear";
        }
        if (aiLabel.contains("scarf") || aiLabel.contains("tie") || aiLabel.contains("chain") || aiLabel.contains("neckwear") || aiLabel.contains("necklace")) {
            return "Neckwear";
        }

        return null; // If Not Detected
    }
    // Samples A Grid Of Pixels Across The Bitmap And Returns The Most Frequently Occurring Named Color
    private String detectDominantColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Tally How Many Sampled Pixels Map To Each Named Color Bucket
        java.util.HashMap<String, Integer> colorCount = new java.util.HashMap<>();

        int step = Math.max(1, Math.min(width, height) / 20); // Sample ~20x20 Grid Regardless Of Image Size

        for (int x = 0; x < width; x += step) {
            for (int y = 0; y < height; y += step) {
                int pixel = bitmap.getPixel(x, y);
                int r = android.graphics.Color.red(pixel);
                int g = android.graphics.Color.green(pixel);
                int b = android.graphics.Color.blue(pixel);

                String namedColor = mapRgbToColorName(r, g, b); // Convert Raw RGB Into A Human-Readable Color Name
                colorCount.put(namedColor, colorCount.getOrDefault(namedColor, 0) + 1);
            }
        }

        // Find The Color Bucket With The Highest Pixel Count
        String dominantColor = "Unknown";
        int maxCount = 0;
        for (java.util.Map.Entry<String, Integer> entry : colorCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantColor = entry.getKey();
            }
        }

        return dominantColor;
    }

    // Converts An RGB Triplet Into The Closest Human-Readable Color Name Using HSV Hue And Brightness
    private String mapRgbToColorName(int r, int g, int b) {
        float[] hsv = new float[3];
        android.graphics.Color.RGBToHSV(r, g, b, hsv); // Hue 0-360, Saturation 0-1, Value/Brightness 0-1

        float hue = hsv[0];
        float sat = hsv[1];
        float val = hsv[2];

        // Low Brightness = Black, High Brightness + Low Saturation = White, Mid = Gray
        if (val < 0.2f) return "Black";
        if (sat < 0.15f && val > 0.85f) return "White";
        if (sat < 0.2f) return "Gray";

        // Map Hue Angle To A Named Color Band
        if (hue < 15 || hue >= 345) return "Red";
        if (hue < 40)  return "Orange";
        if (hue < 70)  return "Yellow";
        if (hue < 150) return "Green";
        if (hue < 195) return "Cyan";
        if (hue < 260) return "Blue";
        if (hue < 290) return "Purple";
        if (hue < 345) return "Pink";

        return "Unknown";
    }
}
