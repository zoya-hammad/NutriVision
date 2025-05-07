package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class HomeScreen : AppCompatActivity() {
    private lateinit var usernameTextView: TextView
    private lateinit var auth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)
        
        auth = Firebase.auth
        usernameTextView = findViewById(R.id.text_view_welcome_user)
        
        // Try to get name from intent first (for fresh sign-ups)
        val nameFromIntent = intent.getStringExtra("name")
        if (nameFromIntent != null) {
            updateWelcomeMessage(nameFromIntent)
        } else {
            // Otherwise fetch from database
            fetchUserDataFromDatabase()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnAddPicture: Button = findViewById(R.id.btn_add_picture)
        btnAddPicture.setOnClickListener {
            startActivity(Intent(this, Start::class.java))
        }
    }
    
    private fun fetchUserDataFromDatabase() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val userRef = Firebase.database.reference.child("users").child(userId)
            
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").getValue(String::class.java)
                        if (name != null) {
                            updateWelcomeMessage(name)
                        } else {
                            // If name is not found, use email as fallback
                            updateWelcomeMessage(currentUser.email ?: "User")
                        }
                    } else {
                        // User data doesn't exist in database
                        updateWelcomeMessage(currentUser.email ?: "User")
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@HomeScreen, "Failed to load user data", Toast.LENGTH_SHORT).show()
                    updateWelcomeMessage("User")
                }
            })
        } else {
            // Not logged in, use generic greeting
            updateWelcomeMessage("User")
        }
    }
    
    private fun updateWelcomeMessage(name: String) {
        usernameTextView.text = "Hi, $name"
    }
}