package com.example.nutrivisionapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
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
    private var doctorNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Fetch doctor's number
        fetchDoctorNumber()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val statusBar = findViewById<BottomNavigationView>(R.id.status).apply {
            // Set home as selected
            selectedItemId = R.id.home

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.home -> {
                        // Already here
                        true
                    }
                    R.id.camera -> {
                        startActivity(Intent(this@HomeScreen, FoodCam::class.java))
                        finish()
                        true
                    }
                    R.id.food_journal -> {
                        startActivity(Intent(this@HomeScreen, FoodJournal::class.java))
                        finish()
                        true
                    }
                    R.id.assistant -> {
                        startActivity(Intent(this@HomeScreen, Assistant::class.java))
                        finish()
                        true
                    }
                    R.id.user -> {
                        startActivity(Intent(this@HomeScreen, User::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }

        // Set up call doctor button
        findViewById<Button>(R.id.contact).setOnClickListener {
            if (doctorNumber != null) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$doctorNumber")
                }
                startActivity(intent)
            } else {
                showAddNumberDialog()
            }
        }

        // Set up text doctor button
        findViewById<Button>(R.id.text).setOnClickListener {
            if (doctorNumber != null) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$doctorNumber")
                }
                startActivity(intent)
            } else {
                showAddNumberDialog()
            }
        }

        // Set up assistant button
        findViewById<Button>(R.id.assistant).setOnClickListener {
            startActivity(Intent(this, Assistant::class.java))
        }

        // Set up add picture button
        findViewById<Button>(R.id.btn_add_picture).setOnClickListener {
            startActivity(Intent(this, FoodCam::class.java))
        }

        // Set up food journal button
        findViewById<Button>(R.id.log_recipe).setOnClickListener {
            startActivity(Intent(this, FoodJournal::class.java))
        }

        // Set up My Recipes button 
        findViewById<Button>(R.id.my_recipes).setOnClickListener {
            startActivity(Intent(this, MyRecipes::class.java))
        }

        val markDateButton = findViewById<Button>(R.id.calender)
        markDateButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
            }
            startActivity(intent)
        }
    }

    private fun showAddNumberDialog() {
        AlertDialog.Builder(this)
            .setTitle("Doctor's Number Not Found")
            .setMessage("Would you like to add your doctor's number?")
            .setPositiveButton("Add Number") { _, _ ->
                // Navigate to User activity
                startActivity(Intent(this, User::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun fetchDoctorNumber() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val userRef = Firebase.database.reference.child("users").child(userId)
            
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        doctorNumber = snapshot.child("doctorNumber").getValue(String::class.java)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@HomeScreen, "Failed to load doctor's number", Toast.LENGTH_SHORT).show()
                }
            })
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