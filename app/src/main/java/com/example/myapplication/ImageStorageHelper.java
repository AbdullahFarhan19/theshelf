package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// A Simple Utility Class That Handles All File-System Operations For Wardrobe Item Photos
// Internal Storage Is Used Because It Is Private To This App And Doesn't Require Extra Permissions
public class ImageStorageHelper {

    // The Sub-Folder Inside The App's Private Internal Storage Where All Photos Are Kept
    private static final String IMAGE_FOLDER = "wardrobe_photos";

    // Saves A Bitmap To Internal Storage And Returns The Absolute File Path
    // Returns null If The Save Failed For Any Reason
    public static String saveBitmapToInternalStorage(Context context, Bitmap bitmap, String fileName) {
        // Get Or Create The Dedicated Folder For Wardrobe Photos
        File directory = new File(context.getFilesDir(), IMAGE_FOLDER);
        if (!directory.exists()) {
            directory.mkdirs(); // mkdirs Creates All Missing Parent Directories As Well
        }

        // The Full File Object For The New Image
        File imageFile = new File(directory, fileName + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            // JPEG At 85 Quality — Good Balance Between File Size And Visual Fidelity
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush(); // Push Any Buffered Bytes To Disk
            return imageFile.getAbsolutePath(); // Return The Path So It Can Be Stored In The DB
        } catch (IOException e) {
            e.printStackTrace(); // Logs The Full Error Trace So We Can Debug Later
            return null; // Signal Failure To The Caller
        }
    }

    // Deletes An Image File From Internal Storage Given Its Absolute Path
    // Returns True If The Deletion Was Successful
    public static boolean deleteImageFromInternalStorage(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) return false;
        File file = new File(absolutePath);
        return file.exists() && file.delete(); // Only Attempt Delete If The File Actually Exists
    }

    // Generates A Unique File Name For A New Image Using The Current Timestamp
    // This Prevents Name Collisions When Multiple Photos Are Taken In Quick Succession
    public static String generateUniqueFileName() {
        return "item_" + System.currentTimeMillis();
    }
}
