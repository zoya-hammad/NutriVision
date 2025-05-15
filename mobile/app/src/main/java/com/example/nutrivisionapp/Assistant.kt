package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class Assistant: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)


        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            // Set assistant as selected
            selectedItemId = R.id.assistant

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@Assistant, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@Assistant, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.progress -> {
                        startActivity(Intent(this@Assistant, Progress::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        // Already here
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@Assistant, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }

}