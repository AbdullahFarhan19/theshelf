package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

// SQLiteOpenHelper Manages The Creation And Version Management Of The Local SQLite Database
// It Helps To Open, Create And Update The Database Without Having To Do Complex Input/Output Operations
public class WardrobeDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME    = "wardrobe.db"; // Represents The Name Of The File Stored On The Device
    private static final int    DB_VERSION = 1; // Current Version Is 1, onUpgrade Is Automatically Called Whenever Updated

    // Table And Column Name Constants To Prevent Typos From Crashing The Program At Runtime
    public static final String TABLE_ITEMS   = "wardrobe_items";
    public static final String COL_ID        = "id";
    public static final String COL_NAME      = "name";
    public static final String COL_CATEGORY  = "category";
    public static final String COL_WEATHER   = "weather";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_COLOR = "color";

    private static WardrobeDatabase instance; // Singleton Pattern — Only One Database Connection Should Exist At A Time To Ensure That Only One Version Of The Database Exists In Memory

    public static synchronized WardrobeDatabase getInstance(Context context) { // synchronized Ensures That If Two Threads Try To Access The Database At The Same Time, A Queue Is Formed
        if (instance == null) {
            instance = new WardrobeDatabase(context.getApplicationContext()); // View.getContext() Would Just Return The Current Activity, However If You Rotate Your Phone (Which Destroys The Activity), No Instance Would Be Returned, Therefore getApplicationContext Is Used Which Returns The Entire Application's Context Rather Than A Single Activity
        }
        return instance;
    }

    // Private Constructor Forces All Callers To Go Through getInstance(), new WardrobeDatabase() Is Banned, Prevents Anyone From Calling WardrobeDatabase And Passing Contexts To It, Doing So Would Store Multiple Contexts To The Same Screen When The Phone Is Rotated Because They Would Be Deleted Upon Rotation, Also Locks Are Enforced On The Data Being Altered, If Two Parts Of The App Try To Access The Database At The Same Time, The App May Crash, This Is Prevented Via A Singular Instance That Accesses The Database
    private WardrobeDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // This SQL Statement Creates The Items Table The Very First Time The App Runs
        String createTable = "CREATE TABLE " + TABLE_ITEMS + " ("
                + COL_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME      + " TEXT NOT NULL, "
                + COL_CATEGORY  + " TEXT NOT NULL, "
                + COL_WEATHER   + " TEXT, "
                + COL_COLOR   + " TEXT, "
                + COL_IMAGE_PATH + " TEXT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL"
                + ")";
        db.execSQL(createTable); // Executes The SQL Query
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple Strategy: Drop The Old Table And Recreate It When The Schema Changes
        // In Production You'd Write ALTER TABLE Migrations Instead To Preserve User Data
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEMS);
        onCreate(db);
    }

    // Inserts A New Wardrobe Item And Returns Its Auto-Generated Row Id, -1 Means Failure
    public long insertItem(WardrobeItem item) {
        SQLiteDatabase db = getWritableDatabase(); // Opens The DB In Read/Write Mode
        ContentValues values = new ContentValues(); // Essentially A Hash Map That Maps DB Keys To Their Values, Used To Insert A Row In The DB
        values.put(COL_NAME,       item.getName());
        values.put(COL_CATEGORY,   item.getCategory());
        values.put(COL_WEATHER,    item.getWeather());
        values.put(COL_IMAGE_PATH, item.getImagePath());
        values.put(COL_COLOR,      item.getColor());
        values.put(COL_TIMESTAMP,  item.getTimestamp());
        long newId = db.insert(TABLE_ITEMS, null, values); // null Means No Null Column Hack Needed, Reads The Hash Map And Inserts The Row Into The Database By Matching The Keys Of Values To The Keys Of The Database, This Is Converted Into An Insert Statement And The Primary Key id Assigned Is Returned, nullColumnHack Is Used When You Want To Insert An Empty Row, In It's Place You Should Specify A Key That Can Have Null Values So That Java Can Insert The Empty Row Into The Table
        db.close(); // Always Close After Writing To Prevent Connection Leaks
        return newId;
    }

    // Removes A Single Item By Its Primary Key
    public void deleteItem(int itemId) {
        SQLiteDatabase db = getWritableDatabase(); // Returns A Writeable Database
        db.delete(TABLE_ITEMS, COL_ID + "=?", new String[]{String.valueOf(itemId)}); // Parameterized Querying Invulnerable To Injection Attacks, Takes itemId Converts Into Array Of Strings
        db.close();
    }

    // Fetches All Items Ordered By Most Recently Added First (Newest At The Top)
    public List<WardrobeItem> getAllItems() {
        List<WardrobeItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase(); // Read-Only Mode Is Safer When Not Writing

        // Raw Query: Select Every Column From The Table Sorted By Timestamp Descending
        Cursor cursor = db.rawQuery( // Basically A Pointer To The Data Rows Returned
                "SELECT * FROM " + TABLE_ITEMS + " ORDER BY " + COL_TIMESTAMP + " DESC", null // SelectionArgs Are Basically Like Template Literals To Be Injected In Place Og =?
        );

        if (cursor.moveToFirst()) { // Moves The Cursor Pointer To The First Row, Returns False If Empty
            do {
                // Build A WardrobeItem From Each Row By Reading Each Column By Its Name
                WardrobeItem item = new WardrobeItem(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)), // Finds The Column Index Number Based On It's Name, Throws An Error If Not Found
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_WEATHER)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_COLOR)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP))
                );
                items.add(item);
            } while (cursor.moveToNext()); // Advances To The Next Row Until None Are Left
        }

        cursor.close(); // Cursors Hold Resources; Always Close Them When Done
        db.close();
        return items;
    }

    // Fetches All Items That Match A Specific Weather Tag, Used By The Suggestion Engine
    public List<WardrobeItem> getItemsByWeather(String weather) {
        List<WardrobeItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        // Parameterised Query — The ? Is Replaced Safely By SQLite To Prevent SQL Injection
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_ITEMS + " WHERE " + COL_WEATHER + "=?",
                new String[]{weather}
        );

        if (cursor.moveToFirst()) {
            do {
                WardrobeItem item = new WardrobeItem(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_WEATHER)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_COLOR)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP))
                );
                items.add(item);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return items;
    }

    // Returns The Total Number Of Items Stored, Useful For Showing An Empty State
    public int getItemCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ITEMS, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0); // Column Index 0 Is The Count Value
        }
        cursor.close();
        db.close();
        return count;
    }
}
