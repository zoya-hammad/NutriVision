package com.example.nutrivisionapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class User : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var linkSignOut: TextView
    private lateinit var ageInput: TextInputEditText
    private lateinit var dietaryRestrictions: TextInputEditText
    private lateinit var allergies: TextInputEditText
    private lateinit var switch: SwitchCompat
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_user)

        switch = findViewById(R.id.darkModeSwitch)
        switch.isChecked = isDarkMode
        switch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }
        auth = FirebaseAuth.getInstance()
        database = Firebase.database.reference

        // Initialize views
        ageInput = findViewById(R.id.age_input)
        dietaryRestrictions = findViewById(R.id.dietary_restrictions)
        allergies = findViewById(R.id.allergies)
        linkSignOut = findViewById(R.id.link_sign_out)

        findViewById<BottomNavigationView>(R.id.status).apply {
            // Set user as selected
            selectedItemId = R.id.user

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        startActivity(Intent(this@User, HomeScreen::class.java))
                        finish()
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@User, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.food_journal -> {
                        startActivity(Intent(this@User, FoodJournal::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        startActivity(Intent(this@User, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        // Already here
                        true
                    }
                    else -> false
                }
            }
        }

        linkSignOut.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            finishAffinity()
        }

        loadUserData()

        findViewById<MaterialButton>(R.id.btn_change_password).setOnClickListener {
            startActivity(Intent(this, Changepass::class.java))
        }

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            saveUserProfile()
        }
    }

    //Getting numbers
    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        database.child("users").child(userId).get().addOnSuccessListener { snapshot ->
            ageInput.setText(snapshot.child("age").value?.toString().orEmpty())
            dietaryRestrictions.setText(snapshot.child("dietaryRestrictions").value?.toString().orEmpty())
            allergies.setText(snapshot.child("allergies").value?.toString().orEmpty())
        }
    }

    // saves to database
    private fun saveUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        val age = ageInput.text.toString()
        val dietaryRestrictionsText = dietaryRestrictions.text.toString()
        val allergiesText = allergies.text.toString()

        val updates = mapOf(
            "age" to age,
            "dietaryRestrictions" to dietaryRestrictionsText,
            "allergies" to allergiesText
        )

        //errors checks ;)
        database.child("users").child(userId).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }
}