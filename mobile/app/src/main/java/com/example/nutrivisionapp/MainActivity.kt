package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = Firebase.auth

        // Check if user is already signed in
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeScreen::class.java))
            finish()
            return
        }

        emailEditText = findViewById(R.id.edit_text_email)
        passwordEditText = findViewById(R.id.edit_text_password)
        val loginButton: Button = findViewById(R.id.button_login)
        val signUpLink: TextView = findViewById(R.id.text_view_sign_up)

        loginButton.setOnClickListener {
            if (validateInputs()) {
                login()
            }
        }

        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }
    }

    private fun validateInputs(): Boolean {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return false
        }

        return true
    }

    private fun login() {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeScreen::class.java))
                    finish()
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "No account found with this email"
                        task.exception?.message?.contains("password is invalid") == true ->
                            "Invalid password"
                        else -> "Login failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }
}