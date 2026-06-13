package com.example.myapplication;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Handles The AI Outfit Suggestions Feature — Fetches Three Daily Suggestions From Gemini
// Users Can Also Request Suggestions On Demand And Specify A Special Occasion For Context
public class SuggestionsActivity extends AppCompatActivity {

    // SharedPreferences Keys Used To Cache Today's Suggestions So We Don't Re-Fetch On Every Open
    private static final String PREFS_SUGGESTIONS    = "suggestions_prefs";
    private static final String KEY_SUGGESTIONS_DATE = "suggestions_date"; // Stores The Date The Suggestions Were Last Fetched
    private static final String KEY_SUGGESTION_1     = "suggestion_1";     // Cached First Outfit Suggestion
    private static final String KEY_SUGGESTION_2     = "suggestion_2";     // Cached Second Outfit Suggestion
    private static final String KEY_SUGGESTION_3     = "suggestion_3";     // Cached Third Outfit Suggestion
    private static final String KEY_WEATHER_TODAY    = "weather_today";    // Cached Weather The User Entered Last Time
    private static final String GEMINI_API_KEY       = "AIzaSyAYl-wBWTkgvQMRvU1Q2TdkbcBJXlrTg14"; // API Key
    // The Key Is Appended As A Query Parameter Rather Than A Header
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY;

    private WardrobeDatabase db;     // Access To The Wardrobe Data For Building The AI Prompt
    private SharedPreferences prefs; // Persistent Storage For Cached Suggestions

    // UI References
    private EditText etWeather;           // The User Types Today's Weather Here, e.g "Hot And Sunny"
    private EditText etOccasion;          // Optional Special Occasion Input, e.g "Wedding" Or "Job Interview"
    private TextView tvSuggestion1;       // Card Text For The First Suggestion
    private TextView tvSuggestion2;       // Card Text For The Second Suggestion
    private TextView tvSuggestion3;       // Card Text For The Third Suggestion
    private CardView card1, card2, card3; // The Three Suggestion Cards
    private LinearLayout llSuggestions;   // Container Holding All Three Cards, Hidden While Loading
    private ProgressBar progressBar;      // Spinner Shown While The API Call Is In Flight
    private TextView tvStatus;            // Status Text Shown Below The Progress Bar
    private ImageView btnBack;            // Back Arrow To Return To MainScreen
    private CardView btnGetSuggestions;   // "Get Suggestions" Primary Action Button
    private CardView btnRefresh;          // Refresh Button To Request Three New Suggestions Today
    private RecyclerView rvOutfitImages1, rvOutfitImages2, rvOutfitImages3; // Recycler Views For Suggestion Cards

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggestions);

        db    = WardrobeDatabase.getInstance(this);
        prefs = getSharedPreferences(PREFS_SUGGESTIONS, MODE_PRIVATE);

        // Wire Up All UI References
        btnBack           = findViewById(R.id.btn_back_suggestions);
        etWeather         = findViewById(R.id.et_weather);
        etOccasion        = findViewById(R.id.et_occasion);
        tvSuggestion1     = findViewById(R.id.tv_suggestion_1);
        tvSuggestion2     = findViewById(R.id.tv_suggestion_2);
        tvSuggestion3     = findViewById(R.id.tv_suggestion_3);
        card1             = findViewById(R.id.card_suggestion_1);
        card2             = findViewById(R.id.card_suggestion_2);
        card3             = findViewById(R.id.card_suggestion_3);
        llSuggestions     = findViewById(R.id.ll_suggestions);
        progressBar       = findViewById(R.id.progress_bar);
        tvStatus          = findViewById(R.id.tv_status);
        btnGetSuggestions = findViewById(R.id.btn_get_suggestions);
        btnRefresh        = findViewById(R.id.btn_refresh);
        rvOutfitImages1   = findViewById(R.id.rv_outfit_images_1);
        rvOutfitImages2   = findViewById(R.id.rv_outfit_images_2);
        rvOutfitImages3   = findViewById(R.id.rv_outfit_images_3);

        btnBack.setOnClickListener(v -> finish()); // Pop This Activity Off The Stack And Go Back To MainScreen

        // "Get Suggestions" Checks The Cache First And Only Hits The API If Needed
        btnGetSuggestions.setOnClickListener(v -> fetchSuggestions(false));

        // "Refresh" Always Hits The API And Overwrites The Daily Cache With Brand New Suggestions
        btnRefresh.setOnClickListener(v -> fetchSuggestions(true));

        // On First Load, Restore Any Suggestions Already Generated Today Without An API Call
        restoreCachedSuggestionsIfToday();
    }

    // Checks Whether We Already Generated Suggestions Today And Restores Them From Cache If So
    private void restoreCachedSuggestionsIfToday() {
        String today      = getTodayDateString(); // e.g "2026-04-25"
        String cachedDate = prefs.getString(KEY_SUGGESTIONS_DATE, "");

        if (today.equals(cachedDate)) {
            // We Have Fresh Cached Suggestions — Show Them Without Hitting The API Again
            String s1            = prefs.getString(KEY_SUGGESTION_1, null);
            String s2            = prefs.getString(KEY_SUGGESTION_2, null);
            String s3            = prefs.getString(KEY_SUGGESTION_3, null);
            String cachedWeather = prefs.getString(KEY_WEATHER_TODAY, "");

            if (s1 != null && s2 != null && s3 != null) {
                etWeather.setText(cachedWeather); // Restore The Weather The User Had Entered Previously
                displaySuggestions(s1, s2, s3);  // Show The Cached Suggestion Cards
            }
        }
    }

    // Builds The AI Prompt From The Wardrobe Data And The User's Inputs, Then Fires The API Call
    // forceRefresh=true Skips The Cache Check And Always Requests New Suggestions From Gemini
    private void fetchSuggestions(boolean forceRefresh) {
        String weatherInput  = etWeather.getText().toString().trim();
        String occasionInput = etOccasion.getText().toString().trim();

        if (weatherInput.isEmpty()) {
            Toast.makeText(this, "Please describe today's weather first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // If Not A Force Refresh And We Already Have Today's Suggestions Cached, Show Them
        String today      = getTodayDateString();
        String cachedDate = prefs.getString(KEY_SUGGESTIONS_DATE, "");
        if (!forceRefresh && today.equals(cachedDate) && occasionInput.isEmpty()) {
            restoreCachedSuggestionsIfToday();
            Toast.makeText(this, "Showing today's suggestions. Tap Refresh for new ones.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gather All Wardrobe Items To Give The AI Context About What The User Owns
        List<WardrobeItem> allItems = db.getAllItems();
        if (allItems.isEmpty()) {
            Toast.makeText(this, "Add some items to your wardrobe first!", Toast.LENGTH_LONG).show();
            return;
        }

        // Build A Plain-Text Summary Of The Wardrobe Items For Inclusion In The Prompt
        StringBuilder wardrobeSummary = new StringBuilder(); // String Builder Is Way More Efficient While Looping
        for (WardrobeItem item : allItems) {
            wardrobeSummary.append("- ")
                    .append(item.getName())
                    .append(" (")
                    .append(item.getColor())
                    .append(", ")
                    .append(item.getCategory())
                    .append(", suitable for: ")
                    .append(item.getWeather())
                    .append(")\n");
        }

        // Compose The Full Prompt — Explicit Format Instructions Ensure Consistent Parsing Later
        String prompt = "You are a personal stylist assistant. The user has the following clothing items in their wardrobe:\n\n"
                + wardrobeSummary.toString()
                + "\nToday's weather: " + weatherInput + ".\n"
                + (occasionInput.isEmpty() ? "" : "Special occasion: " + occasionInput + ".\n")
                + "\nPlease suggest exactly 3 complete outfit combinations using only items from the wardrobe above. "
                + "For each outfit, list the items you'd combine and briefly explain why they work together. "
                + "Format your response EXACTLY like this with no extra text:\n"
                + "OUTFIT 1: [items] — [reason]\n"
                + "OUTFIT 2: [items] — [reason]\n"
                + "OUTFIT 3: [items] — [reason]";

        // Show The Loading Spinner While The Network Request Is In Flight
        showLoadingState();

        // Capture A Final Reference To allItems So The Background Thread Can Access It
        final List<WardrobeItem> itemsForAPI = allItems;

        // Fire The API Call On A Background Thread — Network Operations Must Never Run On The Main Thread
        new Thread(() -> { // Basically Like Hardware Threads, Divides CPU Time To Work On This While Other Processes Run Sequentially
            String response = callGeminiAPI(prompt, itemsForAPI); // Blocking HTTP Call — Safe On A Background Thread
            // Jump Back To The Main Thread To Touch Any UI Elements
            runOnUiThread(() -> handleAPIResponse(response, weatherInput));
        }).start();
    }

    private String callGeminiAPI(String userPrompt, List<WardrobeItem> items) {
        try {
            URL url = new URL(GEMINI_API_URL); // The Key Is Already Baked Into The URL Constant Above
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(); // Used To Send And Receive Data On The Web
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json"); // Tells The Server The Media Type Of The Resource
            conn.setDoOutput(true); // The App Will Indeed Send Data To The Server
            conn.setConnectTimeout(30000); // 30 Second Connection Timeout
            conn.setReadTimeout(60000);    // 60 Second Read Timeout For Longer AI Responses

            // Build The Parts Array — The Text Prompt Goes In First, Then One Image Part Per Item That Has A Photo
            JSONArray partsArray = new JSONArray();

            // First Part Is Always The Text Prompt
            JSONObject textPart = new JSONObject();
            textPart.put("text", userPrompt); // The Actual Prompt Text Goes Into The "text" Field
            partsArray.put(textPart);

            // Append An Inline Image Part For Every Wardrobe Item That Has A Saved Photo On Disk
            for (WardrobeItem item : items) {
                String path = item.getImagePath();
                if (path == null || path.isEmpty()) continue; // Skip Items With No Photo

                File imageFile = new File(path);
                if (!imageFile.exists()) continue; // Skip If The File Was Deleted Outside The App

                // Decode And Compress The Saved JPEG To A Byte Array Suitable For Base64 Encoding
                Bitmap bmp = BitmapFactory.decodeFile(path);
                if (bmp == null) continue; // Skip Unreadable Files

                // Scale Down Large Images Before Encoding To Keep The Request Payload Manageable
                int maxDim = 512;
                if (bmp.getWidth() > maxDim || bmp.getHeight() > maxDim) {
                    float scale = (float) maxDim / Math.max(bmp.getWidth(), bmp.getHeight());
                    bmp = Bitmap.createScaledBitmap(
                            bmp,
                            Math.round(bmp.getWidth()  * scale),
                            Math.round(bmp.getHeight() * scale),
                            true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos); // 75 Quality Keeps Size Small
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                // Label The Image So Gemini Knows Which Wardrobe Item It Belongs To
                JSONObject labelPart = new JSONObject();
                labelPart.put("text", "Photo of wardrobe item: " + item.getName());
                partsArray.put(labelPart);

                // Inline Image Data Part — Gemini Accepts JPEG Images As Base64 In The "inlineData" Field
                JSONObject inlineData = new JSONObject();
                inlineData.put("mimeType", "image/jpeg");
                inlineData.put("data", base64Image);

                JSONObject imagePart = new JSONObject();
                imagePart.put("inlineData", inlineData);
                partsArray.put(imagePart);
            }

            JSONObject contentObject = new JSONObject();
            contentObject.put("parts", partsArray); // Each Content Object Holds An Array Of Parts

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(contentObject); // The Top-Level Contents Array Wraps The Whole Message

            JSONObject requestBody = new JSONObject();
            requestBody.put("contents", contentsArray); // Final Shape: { "contents": [...] }

            // Write The JSON To The Connection's Output Stream
            OutputStream os = conn.getOutputStream();
            os.write(requestBody.toString().getBytes("UTF-8")); // Converting To Bytes To Adhere To Syntax
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read The Successful Response Body Line By Line
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString(); // Return The Raw JSON String For Parsing In handleAPIResponse
            } else {
                // Read The Error Body To Help Debug API Key Or Quota Issues
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder errorSb = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorSb.append(errorLine);
                }
                errorReader.close();
                return null; // Signal Failure To The Caller
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace(); // Logs The Full Error Trace For Debugging Connection Or JSON Issues
            return null;
        }
    }

    // Parses The Gemini Response JSON, Extracts The Three Outfit Suggestions, And Updates The UI
    private void handleAPIResponse(String rawResponse, String weatherInput) {
        if (rawResponse == null) {
            showErrorState("Could Not Reach Gemini. Check Your Internet Connection And API Key.");
            return;
        }

        try {
            // { candidates: [ { content: { parts: [ { text: "..." } ] } } ] }
            JSONObject responseJson   = new JSONObject(rawResponse);
            JSONArray  candidates     = responseJson.getJSONArray("candidates");   // Array Of Candidate Responses
            JSONObject firstCandidate = candidates.getJSONObject(0);               // We Only Requested One Candidate
            JSONObject content        = firstCandidate.getJSONObject("content");   // The Content Wrapper Object
            JSONArray  parts          = content.getJSONArray("parts");             // Array Of Response Parts
            String     aiText         = parts.getJSONObject(0).getString("text").trim(); // The Actual Text Response

            // Parse The Three Outfit Blocks Out Of The AI's Formatted Response
            String outfit1 = extractOutfit(aiText, "OUTFIT 1:");
            String outfit2 = extractOutfit(aiText, "OUTFIT 2:");
            String outfit3 = extractOutfit(aiText, "OUTFIT 3:");

            if (outfit1 == null || outfit2 == null || outfit3 == null) {
                // Gemini Didn't Follow Our Exact Format — Display The Raw Text As A Fallback
                displaySuggestions(aiText, "—", "—");
                return;
            }

            // Cache The Suggestions So We Don't Hit The API Again If The User Reopens This Screen Today
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_SUGGESTIONS_DATE, getTodayDateString());
            editor.putString(KEY_SUGGESTION_1,     outfit1);
            editor.putString(KEY_SUGGESTION_2,     outfit2);
            editor.putString(KEY_SUGGESTION_3,     outfit3);
            editor.putString(KEY_WEATHER_TODAY,    weatherInput);
            editor.apply(); // Writes Asynchronously To Disk Without Blocking The Main Thread

            displaySuggestions(outfit1, outfit2, outfit3); // Render The Three Suggestion Cards

        } catch (JSONException e) {
            e.printStackTrace();
            showErrorState("Failed to read Gemini's response. Please try again.");
        }
    }

    // Extracts A Single Outfit Block From The AI Text Given Its Prefix Label
    // E.G. Passing "OUTFIT 2:" Returns Everything After That Label Up To The Next "OUTFIT" Marker
    private String extractOutfit(String fullText, String label) {
        // fullText Looks Like OUTFIT i: [items] — [reason]
        int startIdx = fullText.indexOf(label);
        if (startIdx == -1) return null; // Label Not Found — AI Deviated From Our Requested Format

        // Determine Where This Outfit Block Ends By Finding Where The Next One Begins
        int nextOutfitIdx = fullText.indexOf("OUTFIT", startIdx + label.length());
        String outfitText;
        if (nextOutfitIdx == -1) {
            outfitText = fullText.substring(startIdx + label.length()); // Last Block Goes To End Of String
        } else {
            outfitText = fullText.substring(startIdx + label.length(), nextOutfitIdx);
        }
        return outfitText.trim(); // Remove Any Surrounding Whitespace Or Newlines
    }

    // Switches The UI Into Loading Mode — Shows The Spinner And Hides The Suggestion Cards
    private void showLoadingState() {
        llSuggestions.setVisibility(View.GONE);  // Hide The Cards While We Wait For A Response
        progressBar.setVisibility(View.VISIBLE); // Show The Spinning Indicator
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Asking Gemini for outfit ideas...");
    }

    // Returns Every Wardrobe Item Whose Name Appears In The Outfit Text —
    // The Horizontal Strip Shows One Chip Per Matched Item
    private List<WardrobeItem> findItemsForOutfit(String outfitText) {
        List<WardrobeItem> matched = new ArrayList<>();
        for (WardrobeItem item : db.getAllItems()) {
            if (outfitText.toLowerCase().contains(item.getName().toLowerCase())) {
                matched.add(item);
            }
        }
        return matched;
    }

    // Wires A Horizontal Recyclerview To The Items Matched From An Outfit Text Block
    private void bindOutfitRecycler(RecyclerView rv, String outfitText) {
        List<WardrobeItem> items = findItemsForOutfit(outfitText);
        rv.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rv.setAdapter(new OutfitImageAdapter(items));
        rv.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // Reveals The Three Suggestion Cards And Populates Them With The AI's Response
    private void displaySuggestions(String s1, String s2, String s3) {
        progressBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        llSuggestions.setVisibility(View.VISIBLE);
        tvSuggestion1.setText(s1);
        tvSuggestion2.setText(s2);
        tvSuggestion3.setText(s3);

        bindOutfitRecycler(rvOutfitImages1, s1);
        bindOutfitRecycler(rvOutfitImages2, s2);
        bindOutfitRecycler(rvOutfitImages3, s3);
    }

    // Shows A Human-Readable Error Message When The API Call Fails Or The Response Is Malformed
    private void showErrorState(String message) {
        progressBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(message);
        llSuggestions.setVisibility(View.GONE); // Keep The Cards Hidden Until We Have Real Data
    }

    // Returns Today's Date As "yyyy-MM-dd" — Used As The Key For The Daily Suggestion Cache
    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}