package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) { // AppCompatActivity Gives Backwards Compatibility For Previous Android Versions, AppCompatActivity Is A Subclass Of FragmentActivity Which Is A Subclass Of Activity Which Every Activity Must Inherit From
        super.onCreate(savedInstanceState); // If The Activity Never Existed Before Bundle Is null, If It Did And Was Terminated, Bundle Contains The Activity's Previously Saved State
        SharedPreferences preferences = getSharedPreferences("App Data",MODE_PRIVATE); // An XML File That Is Created When The App Is Run, Called My App Data, Private So that Other Apps Can't Access It
       //boolean hasSeen = preferences.getBoolean("seen_starting_screen", false); // Get The Boolean Value s_s_s From The Xml File, If It Doesn't Exist Consider It False, This Prevents The App From Crashing
        boolean hasSeen = false; // For testing

        if(hasSeen){ // Contexts Are Basically Information Given To The OS About Where You Are Currently At
            Intent i = new Intent(MainActivity.this, MainScreen.class); // Intents Are Messengers That Relay Information Between Components, Used For Starting Activities, Performing Background Tasks Like Downloading And Giving Out System Wide Broadcasts Like Battery Is Low
            startActivity(i);
            finish(); // Deletes current activity from memory
            return;

        }

        setContentView(R.layout.activity_main); // Sets UI To XML File, Class R Is Used Contains IDs To Reference Different Resources

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { // Assigning A New Rule To The Back Button, onBackPressCallback(true) Means That This Rule Is Currently Active And Operational
           @Override
            public void handleOnBackPressed() { // Runs when back is pressed, normally contains finish()
                // Because it is empty, nothing happens when back is pressed
            }
        });

        ((Button) findViewById(R.id.btn_next)).setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v){
                SharedPreferences.Editor editor = preferences.edit(); // Editor Cuz You Cant Change The Xml File Directly, Editor Creates A Temporary Staging Area In Ram, This Is Needed Because If It Was Absent, Two Components Of The App May Be Making Changes To The Xml At The Same Time, Causing Errors
                editor.putBoolean("seen_starting_screen", true); // Create And Set s_s_s
                editor.apply(); // Save Staged Changes

                Intent i = new Intent(MainActivity.this, MainScreen.class); // First Arg Is Your Current Activity And The Second Is The One You Wanna Go To; An Intent Is A Request To Android To Open A New Activity
                startActivity(i);

                finish(); // Removes MainActivity From The Back Stack Of Activities
            }
        });


    }

}