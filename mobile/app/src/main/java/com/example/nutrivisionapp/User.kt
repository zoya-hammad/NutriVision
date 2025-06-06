package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)


        auth = FirebaseAuth.getInstance()
        database = Firebase.database.reference

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
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

        linkSignOut= findViewById(R.id.link_sign_out)
        linkSignOut.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            finishAffinity()
        }

        loadUserData()

        findViewById<MaterialButton>(R.id.btn_change_password).setOnClickListener {
            startActivity(Intent(this, Changepass::class.java))
        }

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            saveContactInfo()
        }
    }

    //Getting numbers
    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        database.child("users").child(userId).get().addOnSuccessListener { snapshot ->
            findViewById<TextInputEditText>(R.id.doctor_number).setText(
                snapshot.child("doctorNumber").value?.toString().orEmpty()
            )
            findViewById<TextInputEditText>(R.id.hospital_number).setText(
                snapshot.child("hospitalNumber").value?.toString().orEmpty()
            )
        }
    }

    // saves to database
    private fun saveContactInfo() {
        val userId = auth.currentUser?.uid ?: return
        val doctorNumber = findViewById<TextInputEditText>(R.id.doctor_number).text.toString()
        val hospitalNumber = findViewById<TextInputEditText>(R.id.hospital_number).text.toString()

        val updates = mapOf(
            "doctorNumber" to doctorNumber,
            "hospitalNumber" to hospitalNumber
        )

        //errors checks ;)
        database.child("users").child(userId).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Information saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
    }
}