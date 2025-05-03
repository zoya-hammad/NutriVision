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

class SignUp : AppCompatActivity() {

    lateinit var username : TextInputEditText
    lateinit var email : TextInputEditText
    lateinit var password : TextInputEditText
    lateinit var passwordCheck : TextInputEditText
    lateinit var textSignIn : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)
        initialiseVars()
        setupSignInLink()
        Toast.makeText(this,"user: $username, email: $email, pass: $password, confirmpass: $passwordCheck",Toast.LENGTH_LONG).show()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    private fun initialiseVars() {
        username = findViewById(R.id.edit_text_username)
        email = findViewById(R.id.edit_text_email)
        password = findViewById(R.id.edit_text_password)
        passwordCheck = findViewById(R.id.edit_text_confirm_password)

    }

    private fun setupSignInLink() {
        textSignIn = findViewById(R.id.text_view_sign_in)
        textSignIn.setOnClickListener{
            val intent = Intent(this@SignUp, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}