package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    lateinit var usernameEditText :TextInputEditText
    lateinit var passwordEditText :TextInputEditText
    lateinit var textSignUp : TextView
    lateinit var signInButton : MaterialButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        initialiseVars()
        setupSignUpLink()
        signInButton = findViewById(R.id.button_sign_in)
        signInButton.setOnClickListener{
            signIn()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initialiseVars() {
        usernameEditText = findViewById(R.id.edit_text_username)
        passwordEditText = findViewById(R.id.edit_text_password)
    }

    private fun setupSignUpLink() {
        textSignUp = findViewById(R.id.text_view_sign_up)
        textSignUp.setOnClickListener{
            val intent = Intent(this@MainActivity, SignUp::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun signIn() {
        Toast.makeText(this,"username: ${usernameEditText.text}, pass: ${passwordEditText.text}", Toast.LENGTH_LONG).show()
    }

}