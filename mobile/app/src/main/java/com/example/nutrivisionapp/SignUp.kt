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

class SignUp : AppCompatActivity() {

    lateinit var usernameEditText : TextInputEditText
    lateinit var emailEditText : TextInputEditText
    lateinit var passwordEditText : TextInputEditText
    lateinit var passwordCheckEditText : TextInputEditText
    lateinit var textSignInEditText : TextView
    lateinit var signupButton : MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)
        initialiseVars()
        setupSignInLink()
        signupButton = findViewById(R.id.button_sign_up)
        signupButton.setOnClickListener{
            signUp()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    private fun initialiseVars() {
        usernameEditText = findViewById(R.id.edit_text_username)
        emailEditText = findViewById(R.id.edit_text_email)
        passwordEditText = findViewById(R.id.edit_text_password)
        passwordCheckEditText = findViewById(R.id.edit_text_confirm_password)

    }

    private fun setupSignInLink() {
        textSignInEditText = findViewById(R.id.text_view_sign_in)
        textSignInEditText.setOnClickListener{
            val intent = Intent(this@SignUp, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun signUp() {
        Toast.makeText(this,"user: ${usernameEditText.text}, email: ${emailEditText.text}, pass: ${passwordEditText.text}, confirmpass: ${passwordCheckEditText.text}",Toast.LENGTH_LONG).show()
    }
}