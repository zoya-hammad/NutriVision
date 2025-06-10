package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class SignUp : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var termsCheckBox: CheckBox
    private lateinit var termsLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = Firebase.auth
        database = Firebase.database

        emailEditText = findViewById(R.id.edit_text_email)
        nameEditText = findViewById(R.id.edit_text_name)
        passwordEditText = findViewById(R.id.edit_text_password)
        confirmPasswordEditText = findViewById(R.id.edit_text_confirm_password)
        termsCheckBox = findViewById(R.id.checkbox_terms)
        termsLink = findViewById(R.id.text_view_terms)
        val signInText: TextView = findViewById(R.id.text_view_sign_in)
        val signUpButton: Button = findViewById(R.id.button_sign_up)

        termsLink.setOnClickListener {
            startActivityForResult(Intent(this, TermsAndConditions::class.java), TERMS_REQUEST_CODE)
        }

        signUpButton.setOnClickListener {
            if (validateInputs()) {
                signUp()
            }
        }

        signInText.setOnClickListener {
            // Navigating to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TERMS_REQUEST_CODE) {
            // If user clicked agree in TermsAndConditions, check the checkbox
            termsCheckBox.isChecked = true
        }
    }

    companion object {
        private const val TERMS_REQUEST_CODE = 1001
    }

    private fun validateInputs(): Boolean {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val name = nameEditText.text.toString()

        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return false
        }

        if (name.isEmpty()) {
            nameEditText.error = "Name is required"
            return false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return false
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordEditText.error = "Please confirm your password"
            return false
        }

        if (password != confirmPassword) {
            confirmPasswordEditText.error = "Passwords do not match"
            return false
        }

        if (!termsCheckBox.isChecked) {
            Toast.makeText(this, "Please accept the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun signUp() {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()
        val name = nameEditText.text.toString()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Save user data to Firebase Database
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val userRef = database.reference.child("users").child(userId)
                        val userData = HashMap<String, Any>()
                        userData["email"] = email
                        userData["name"] = name
                        
                        userRef.setValue(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, HomeScreen::class.java)
                                intent.putExtra("name", name)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("email address is already in use") == true ->
                            "Email is already registered"
                        task.exception?.message?.contains("badly formatted") == true ->
                            "Invalid email format"
                        task.exception?.message?.contains("password is too weak") == true ->
                            "Password is too weak. Use at least 6 characters"
                        else -> "Sign up failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }
}