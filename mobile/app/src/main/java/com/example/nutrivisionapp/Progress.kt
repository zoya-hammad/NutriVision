package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class Progress: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            // Set progress as selected
            selectedItemId = R.id.progress

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@Progress, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@Progress, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.progress -> {
                        // Already here
                        true
                    }
                    R.id.assistant -> {
                        startActivity(Intent(this@Progress, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@Progress, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }

}