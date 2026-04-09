package com.example.myapplication;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

public class MainScreen extends AppCompatActivity {
    ActivityResultLauncher<Void> cameraLauncher; // Launches An Activity And Can Be Used To Get Output From It, It Takes No Input
    ActivityResultLauncher<String> permissionLauncher; // Takes A String Input So That It Knows Which Permission It Needs To Ask
    private ImageView currentDialogImageView;
    private Spinner currentDialogSpinnerCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        cameraLauncher = registerForActivityResult( // Tells Android To Expect An Activity/applaunch In The Near Future
                new ActivityResultContracts.TakePicturePreview(), // Open The Default Camera, Let The User Snap A Photo And Then Come Back With A Small Preview
                new ActivityResultCallback<Bitmap>(){ // This Wakes Up When The Camera App Is Closed/the Picture Is Taken
                    @Override
                    public void onActivityResult(Bitmap result){ // When The Pic Is Delivered
                        if(result != null){
                            Toast.makeText(MainScreen.this, "Item Captured Successfully!", Toast.LENGTH_SHORT).show(); // LENGTH_SHORT Makes The Toast Lingers For A Small Amount Of Time, LENGTH_LONG Would Make It Linger For Longer

                            if(currentDialogImageView != null){
                                currentDialogImageView.setImageBitmap(result);
                            }
                            if(currentDialogSpinnerCategory != null){
                                analyzeImageWithMLKit(result, currentDialogSpinnerCategory);
                            }

                        }
                        else{
                            Toast.makeText(MainScreen.this, "Failed To Capture The Picture", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

        );

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), // Displays The Android Popup Asking For Permisson
                new ActivityResultCallback<Boolean>(){
                    @Override
                    public void onActivityResult(Boolean granted){
                        if(granted){
                            cameraLauncher.launch(null);
                        }
                        else{
                            Toast.makeText(MainScreen.this, "The app needs camera permissions to take photos!", Toast.LENGTH_LONG).show();
                        }

                    }
                }

        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        ImageView btnHistory = findViewById(R.id.btn_history);
        CardView btnAdd = findViewById(R.id.btn_add);
        ImageView btnSuggestions = findViewById(R.id.btn_suggestions);

        // History Button
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainScreen.this, "Opening History...", Toast.LENGTH_SHORT).show();
            }
        });

        // Add Item Button
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddItemDialog();

            }
        });

        // Suggestions Button
        btnSuggestions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainScreen.this, "Generating Today's Outfit...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddItemDialog(){
        LayoutInflater inflater = LayoutInflater.from(this); // Take XML File And "Inflates" Into A Java View Object, this Means The Current Screen
        View dialogView = inflater.inflate(R.layout.dialog_add_item, null); // This Executes The Inflation, Builds It In Memory, null Means No Parent Has Been Assigned, The Current Screen Is The Parent

        AlertDialog.Builder builder = new AlertDialog.Builder(this); // Builder Used To Build The Dialog Object
        builder.setView(dialogView); // Set Builder's View To Inflated Object,
        AlertDialog dialog = builder.create(); // Finishes The Instruction

        currentDialogImageView = dialogView.findViewById(R.id.dialog_image_preview); // Selects The Image Inside The Dialog, dialogView.findViewById Used Instead Of findViewById Because Simply findViewById Would Cause It To Look In MainScreen And Not DialogView
        EditText inputName = dialogView.findViewById(R.id.dialog_input_name);
        Spinner currentDialogSpinnerCategory = dialogView.findViewById(R.id.dialog_spinner_category);
        Spinner spinnerWeather = dialogView.findViewById(R.id.dialog_spinner_weather);
        Button btnCancel = dialogView.findViewById(R.id.dialog_btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.dialog_btn_save);

        currentDialogImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                permissionLauncher.launch(android.Manifest.permission.CAMERA); // Launches Permission Dialog Box To Launch Camera
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Handle Save Button
        btnSave.setOnClickListener(v -> {
            String itemName = inputName.getText().toString().trim();
            String category = currentDialogSpinnerCategory.getSelectedItem().toString();
            String weather = spinnerWeather.getSelectedItem().toString();

            // Basic validation
            if (itemName.isEmpty() || category.equals("Select Category...")) {
                Toast.makeText(this, "Please provide a name and category", Toast.LENGTH_SHORT).show();
                return;
            }

            // Here is where you would eventually save this data to your database (e.g., SQLite or Firebase)
            Toast.makeText(this, "Saved " + itemName + " to The Shelf!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Show the window
        dialog.show();
    }

    private void analyzeImageWithMLKit(Bitmap bitmap, Spinner spinnerCategory){
        InputImage image = InputImage.fromBitmap(bitmap, 0); // Converts bitmap Into A Useable Format For The AI

        ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS); // Wakes Up AI, Default Image Labeller With No Internet Required Is Used

        labeler.process(image).addOnSuccessListener(labels -> { // Asynchronouse CallBack, Waits For The AI To Finish While The Rest Of The App Keeps Running Smoothly
            for(ImageLabel label : labels){ // Iterates Through All Labels Generated
                String text = label.getText().toLowerCase();
                float confidence = label.getConfidence();

                if(confidence > 0.7f){
                    int mappedIndex = mapAILabelToSpinnerIndex(text);

                    if(mappedIndex != 0){
                        spinnerCategory.setSelection(mappedIndex);
                        Toast.makeText(MainScreen.this, "AI Has Identified The Image Provided! Check The Last Option Of The DropDown Menu", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }).addOnFailureListener(e -> {
            e.printStackTrace(); // Logs Thrown Exception
        });

    }
    private int mapAILabelToSpinnerIndex(String aiLabel){
        if (aiLabel.contains("shirt") || aiLabel.contains("top") || aiLabel.contains("blouse")) {
            return 1;
        }
        if (aiLabel.contains("pants") || aiLabel.contains("jeans") || aiLabel.contains("shorts") || aiLabel.contains("trousers")) {
            return 2;
        }
        if (aiLabel.contains("jacket") || aiLabel.contains("coat") || aiLabel.contains("outerwear") || aiLabel.contains("hoodie")) {
            return 3;
        }
        if (aiLabel.contains("shoe") || aiLabel.contains("footwear") || aiLabel.contains("sneaker") || aiLabel.contains("boot")) {
            return 4;
        }
        if (aiLabel.contains("wrist wear") || aiLabel.contains("ring") || aiLabel.contains("watch") || aiLabel.contains("bracelet")) {
            return 5;
        }
        if(aiLabel.contains("headwear") || aiLabel.contains("hat") || aiLabel.contains("cap") || aiLabel.contains("crown") || aiLabel.contains("headband")){
            return 6;
        }
        if(aiLabel.contains("glasses") || aiLabel.contains("facewear") || aiLabel.contains("earrings") || aiLabel.contains("nose ring")){
            return 7;
        }
        if(aiLabel.contains("scarf") || aiLabel.contains("tie") || aiLabel.contains("chain") || aiLabel.contains("neckwear") || aiLabel.contains("necklace")){
            return 8;
        }

        return 0; // If Not Detected
    }
}