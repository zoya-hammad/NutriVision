package com.example.nutrivisionapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    lateinit var username :TextInputEditText
    lateinit var password :TextInputEditText
    lateinit var textSignUp : TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        initialiseVars()
        setupSignUpLink()
        Toast.makeText(this,"username: $username, pass: $password", Toast.LENGTH_LONG).show()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initialiseVars() {
        username = findViewById(R.id.edit_text_username)
        password = findViewById(R.id.edit_text_password)
    }

    private fun setupSignUpLink() {
        textSignUp = findViewById(R.id.text_view_sign_up)
        textSignUp.setOnClickListener{
            val intent = Intent(this@MainActivity, SignUp::class.java)
            startActivity(intent)
            finish()
        }
    }
}