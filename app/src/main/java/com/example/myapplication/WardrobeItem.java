package com.example.myapplication;

// A Plain Data Model That Represents A Single Clothing Item Stored In The Database
// Each Field Maps Directly To A Column In The SQLite Table
public class WardrobeItem {

    private int id;           // Auto-Incremented Primary Key From SQLite
    private String name;      // User-Given Name For The Item, E.g. "Blue Denim Jacket"
    private String category;  // One Of The Predefined Categories Like "Tops", "Shoes" Etc.
    private String weather;   // The Weather Tag The User Assigned, E.g. "Cold", "Hot"
    private String imagePath; // Absolute File Path On Device Where The Item's Photo Is Saved
    private String color;     // Color Of Clothing Item
    private long timestamp;   // Unix Timestamp In Milliseconds Recording When The Item Was Added

    // Full Constructor Used When Reading A Row Back From The Database
    public WardrobeItem(int id, String name, String category, String weather, String imagePath, String color, long timestamp) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.weather = weather;
        this.imagePath = imagePath;
        this.color = color;
        this.timestamp = timestamp;
    }

    // Lightweight Constructor Used When Building A New Item Before It Gets An Id From The DB
    public WardrobeItem(String name, String category, String weather, String color, String imagePath) {
        this.name = name;
        this.category = category;
        this.weather = weather;
        this.imagePath = imagePath;
        this.color = color;
        this.timestamp = System.currentTimeMillis(); // Stamp The Moment Of Creation
    }

    // Standard Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getWeather() { return weather; }
    public String getImagePath() { return imagePath; }
    public String getColor() { return color; }
    public long getTimestamp() { return timestamp; }

    // Setter For Id Because SQLite Assigns It After The Insert, Not Before
    public void setId(int id) { this.id = id; }
}
